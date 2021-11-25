package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;
import static java.util.Collections.unmodifiableList;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.excludeField;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.getFieldValue;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.getResultPath;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.includeFieldPath;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapRequest;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapSchema;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapTransform;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.putMapValue;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.TraversalControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public class HoistField extends AbstractTransform {

  private final String typeName;

  private final String targetFieldName;

  private final List<String> sourceFieldPath;

  private GraphQLSchema transformedSchema;

  private boolean targetFieldList = false;

  public HoistField(String typeName, String targetFieldName, List<String> sourceFieldPath) {
    if (sourceFieldPath.size() <= 1) {
      throw new IllegalArgumentException("Source field path must contain at least 2 fields.");
    }

    this.typeName = typeName;
    this.targetFieldName = targetFieldName;
    this.sourceFieldPath = unmodifiableList(sourceFieldPath);
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema, @NonNull TransformContext context) {
    if (originalSchema.getObjectType(typeName) == null) {
      throw new TransformException(String.format("Object type '%s' not found.", typeName));
    }

    var schemaMapping = SchemaMapping.newSchemaMapping()
        .objectType((objectType, traverserContext) -> {
          if (!typeName.equals(objectType.getName())) {
            return CONTINUE;
          }

          var sourceField = findSourceField(objectType, sourceFieldPath);
          var hoistedField = sourceField.transform(builder -> builder.name(targetFieldName)
              .type(wrapListType(sourceField.getType())));

          return changeNode(traverserContext, objectType.transform(builder -> builder.field(hoistedField)));
        })
        .build();

    transformedSchema = mapSchema(originalSchema, schemaMapping);

    return transformedSchema;
  }

  private GraphQLOutputType wrapListType(GraphQLOutputType type) {
    return targetFieldList ? GraphQLNonNull.nonNull(GraphQLList.list(type)) : type;
  }

  private GraphQLFieldDefinition findSourceField(GraphQLObjectType objectType, List<String> fieldPath) {
    var fieldName = fieldPath.get(0);
    var fieldPathSize = fieldPath.size();

    var field = Optional.ofNullable(objectType.getFieldDefinition(fieldName))
        .orElseThrow(() -> new TransformException(String.format("Object field '%s' not found.", fieldName)));

    if (GraphQLTypeUtil.unwrapNonNull(field.getType()) instanceof GraphQLList) {
      if (targetFieldList) {
        throw new TransformException("Source field path contains more than one list field.");
      }

      targetFieldList = true;
    }

    if (fieldPathSize == 1) {
      return field;
    }

    var fieldType = GraphQLTypeUtil.unwrapAll(field.getType());

    if (!(fieldType instanceof GraphQLObjectType)) {
      throw new TransformException("Non-leaf path segments must represent object types.");
    }

    return findSourceField((GraphQLObjectType) fieldType, fieldPath.subList(1, fieldPathSize));
  }

  @Override
  public CompletableFuture<Result> transform(@NonNull Request originalRequest,
      @NonNull Function<Request, CompletableFuture<Result>> next) {
    var hoistedFields = new ArrayList<HoistedField>();

    var mapping = RequestMapping.newRequestMapping()
        .field(environment -> {
          if (!isFieldMatching(environment)) {
            return CONTINUE;
          }

          var targetKey = environment.getField()
              .getResultKey();

          // Keep track of all hoisted fields in the selection tree
          hoistedFields.add(new HoistedField(getResultPath(environment.getTraverserContext()), targetKey));

          return hoistField(environment);
        })
        .build();

    return next.apply(mapRequest(originalRequest, transformedSchema, mapping))
        .thenApply(result -> dehoistFields(result, unmodifiableList(hoistedFields)));
  }

  private boolean isFieldMatching(QueryVisitorFieldEnvironment environment) {
    var fieldsContainer = environment.getFieldsContainer();
    var fieldDefinition = environment.getFieldDefinition();

    return typeName.equals(fieldsContainer.getName()) && targetFieldName.equals(fieldDefinition.getName());
  }

  private TraversalControl hoistField(QueryVisitorFieldEnvironment environment) {
    var parentEnvironment = environment.getParentEnvironment();
    var parentField = parentEnvironment.getField();

    return changeNode(parentEnvironment.getTraverserContext(),
        parentField.transform(builder -> builder.selectionSet(transformSelectionSet(parentField.getSelectionSet()))));
  }

  private SelectionSet transformSelectionSet(SelectionSet selectionSet) {
    return includeFieldPath(excludeField(selectionSet, targetFieldName), sourceFieldPath);
  }

  @SuppressWarnings("unchecked")
  private Result dehoistFields(Result result, List<HoistedField> hoistedFields) {
    Map<String, Object> data = (Map<String, Object>) hoistedFields.stream()
        .reduce(result.getData(), this::dehoistField, TransformUtils::noopCombiner);

    return result.transform(builder -> builder.data(data));
  }

  private Object dehoistField(Object data, HoistedField hoistedField) {
    return mapTransform(data, hoistedField.getBasePath(), fieldContainer -> putMapValue(fieldContainer,
        hoistedField.getTargetKey(), getFieldValue(fieldContainer, sourceFieldPath)));
  }

  @Getter
  private static class HoistedField {

    private final List<String> basePath;

    private final String targetKey;

    public HoistedField(List<String> basePath, String targetKey) {
      this.basePath = basePath;
      this.targetKey = targetKey;
    }
  }
}
