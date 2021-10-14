package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.excludeField;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.includeFieldPath;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapRequest;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapSchema;

import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.dotwebstack.graphql.orchestrate.Request;

public class HoistField implements Transform {

  private final String typeName;

  private final String targetFieldName;

  private final List<String> sourceFieldPath;

  private GraphQLSchema transformedSchema;

  public HoistField(String typeName, String targetFieldName, List<String> sourceFieldPath) {
    if (sourceFieldPath.isEmpty()) {
      throw new IllegalArgumentException("Source field path must contain at least 1 segment.");
    }

    this.typeName = typeName;
    this.targetFieldName = targetFieldName;
    this.sourceFieldPath = Collections.unmodifiableList(sourceFieldPath);
  }

  @Override
  public GraphQLSchema transformSchema(GraphQLSchema originalSchema) {
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

  @Override
  public Request transformRequest(Request originalRequest) {
    var mapping = RequestMapping.newRequestMapping()
        .field(environment -> {
          var fieldsContainer = environment.getFieldsContainer();
          var fieldDefinition = environment.getFieldDefinition();

          if (!typeName.equals(fieldsContainer.getName()) || !targetFieldName.equals(fieldDefinition.getName())) {
            return CONTINUE;
          }

          var parentEnvironment = environment.getParentEnvironment();
          var parentField = parentEnvironment.getField();

          return changeNode(parentEnvironment.getTraverserContext(), parentField
              .transform(builder -> builder.selectionSet(transformSelectionSet(parentField.getSelectionSet()))));
        })
        .build();

    return mapRequest(originalRequest, transformedSchema, mapping);
  }

  private GraphQLFieldDefinition findSourceField(GraphQLObjectType objectType, List<String> fieldPath) {
    var fieldName = fieldPath.get(0);
    var fieldPathSize = fieldPath.size();

    var field = Optional.ofNullable(objectType.getFieldDefinition(fieldName))
        .orElseThrow(() -> new TransformException(String.format("Object field '%s' not found.", fieldName)));

    if (fieldPathSize == 1) {
      return field;
    }

    var fieldType = GraphQLTypeUtil.unwrapAll(field.getType());

    if (!(fieldType instanceof GraphQLObjectType)) {
      throw new TransformException("Non-leaf path segments must represent object types.");
    }

    return findSourceField((GraphQLObjectType) fieldType, fieldPath.subList(1, fieldPathSize));
  }

  private SelectionSet transformSelectionSet(SelectionSet selectionSet) {
    return includeFieldPath(excludeField(selectionSet, targetFieldName), sourceFieldPath);
  }
}
