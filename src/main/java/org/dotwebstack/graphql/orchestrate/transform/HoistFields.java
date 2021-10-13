package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class HoistFields implements Transform {

  private final String typeName;

  private final String targetFieldName;

  private final List<String> sourceFieldPath;

  public HoistFields(String typeName, String targetFieldName, List<String> sourceFieldPath) {
    if (sourceFieldPath.isEmpty()) {
      throw new TransformException("Source field path must contain at least 1 segment.");
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

    return TransformUtils.mapSchema(originalSchema, schemaMapping);
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
}
