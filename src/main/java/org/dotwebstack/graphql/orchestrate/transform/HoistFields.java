package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapRequest;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapSchema;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.Field;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.TraversalControl;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dotwebstack.graphql.orchestrate.Request;

public class HoistFields implements Transform {

  private final String typeName;

  private final String targetFieldName;

  private final List<String> sourceFieldPath;

  private GraphQLSchema originalSchema;

  private GraphQLSchema transformedSchema;

  public HoistFields(String typeName, String targetFieldName, List<String> sourceFieldPath) {
    if (sourceFieldPath.isEmpty()) {
      throw new IllegalArgumentException("Source field path must contain at least 1 segment.");
    }

    this.typeName = typeName;
    this.targetFieldName = targetFieldName;
    this.sourceFieldPath = Collections.unmodifiableList(sourceFieldPath);
  }

  @Override
  public GraphQLSchema transformSchema(GraphQLSchema originalSchema) {
    this.originalSchema = originalSchema;

    if (originalSchema.getObjectType(typeName) == null) {
      throw new TransformException(String.format("Object type '%s' not found.", typeName));
    }

    var schemaMapping = SchemaMapping.newSchemaMapping()
        .objectType((objectType, context) -> {
          if (!typeName.equals(objectType.getName())) {
            return CONTINUE;
          }

          var hoistedField =
              findSourceField(objectType, sourceFieldPath).transform(builder -> builder.name(targetFieldName));

          return changeNode(context, objectType.transform(builder -> builder.field(hoistedField)));
        })
        .build();

    transformedSchema = mapSchema(originalSchema, schemaMapping);

    return transformedSchema;
  }

  private GraphQLFieldDefinition findSourceField(GraphQLObjectType objectType, List<String> fieldPath) {
    var pathSegment = fieldPath.get(0);
    var pathSize = fieldPath.size();

    var field = Optional.ofNullable(objectType.getFieldDefinition(pathSegment))
        .orElseThrow(() -> new TransformException(String.format("Object field '%s' not found.", pathSegment)));

    if (pathSize == 1) {
      return field;
    }

    var fieldType = GraphQLTypeUtil.unwrapAll(field.getType());

    if (!(fieldType instanceof GraphQLObjectType)) {
      throw new TransformException("Non-leaf path segments must represent object types.");
    }

    return findSourceField((GraphQLObjectType) fieldType, fieldPath.subList(1, pathSize));
  }

  @Override
  public Request transformRequest(Request originalRequest) {
    var mapping = RequestMapping.newRequestMapping()
        .field(environment -> {
          var fieldsContainer = environment.getFieldsContainer();
          var fieldDefinition = environment.getFieldDefinition();

          if (typeName.equals(fieldsContainer.getName()) && targetFieldName.equals(fieldDefinition.getName())) {
            var parentField = environment.getParentEnvironment()
                .getField();

            var parentContext = environment.getParentEnvironment()
                .getTraverserContext();

            return changeNode(parentContext, parentField.transform(builder -> builder.selectionSet(
                transformSelectionSet(parentField.getSelectionSet()))));
          }

          return CONTINUE;
        })
        .build();

    return mapRequest(originalRequest, transformedSchema, mapping);
  }

  private TraversalControl transformField(QueryVisitorFieldEnvironment environment) {
    var fieldsContainer = environment.getFieldsContainer();
    var fieldDefinition = environment.getFieldDefinition();

    if (!typeName.equals(fieldsContainer.getName()) || !targetFieldName.equals(fieldDefinition.getName())) {
      return CONTINUE;
    }

    var parentField = environment.getParentEnvironment()
        .getField();

    var parentContext = environment.getParentEnvironment()
        .getTraverserContext();

    return changeNode(parentContext, parentField.transform(builder -> builder.selectionSet(
        transformSelectionSet(parentField.getSelectionSet()))));
  }

  private SelectionSet transformSelectionSet(SelectionSet selectionSet) {
    return selectionSet.transform(builder -> builder.selections(selectionSet.getSelections()
        .stream()
        .flatMap(selection -> selection instanceof Field ? transformField((Field) selection) : Stream.of(selection))
        .collect(Collectors.toList())));
  }

  private Stream<Field> transformField(Field field) {
    if (targetFieldName.equals(field.getName())) {
      return Stream.empty();
    }

    return Stream.of(field);
  }

  private static List<Selection> excludeField(SelectionSet selectionSet, String fieldName) {
    return selectionSet.getSelections()
        .stream()
        .filter(selection -> (!(selection instanceof Field &&
            fieldName.equals(((Field) selection).getName()))))
        .collect(Collectors.toList());
  }

//  private static List<Selection> includeField(SelectionSet selectionSet, List<String> fieldPath) {
//    var fieldName = fieldPath.get(0);
//
//    var field = selectionSet.getSelections()
//        .stream()
//        .map(selection -> {
//          return selection;
//        })
//        .collect(Collectors.toList());
//
//    return new Field("foo");
//  }
}
