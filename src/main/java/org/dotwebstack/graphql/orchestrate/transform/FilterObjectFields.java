package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.util.TreeTransformerUtil.changeNode;
import static org.dotwebstack.graphql.orchestrate.transform.TransformUtils.mapSchema;

import graphql.schema.GraphQLSchema;
import java.util.stream.Collectors;
import lombok.NonNull;

public class FilterObjectFields extends AbstractTransform {

  private final ObjectFieldFilter filter;

  public FilterObjectFields(@NonNull ObjectFieldFilter filter) {
    this.filter = filter;
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema, @NonNull TransformContext context) {
    return mapSchema(originalSchema, SchemaMapping.newSchemaMapping()
        .objectType(((objectType, traverserContext) -> {
          var fieldDefinitions = objectType.getFieldDefinitions()
              .stream()
              .filter(fieldDefinition -> filter.apply(objectType.getName(), fieldDefinition.getName(), fieldDefinition))
              .collect(Collectors.toList());

          if (fieldDefinitions.isEmpty()) {
            throw new TransformException("Object types must contain at least 1 field.");
          }

          return changeNode(traverserContext, objectType.transform(builder -> builder.replaceFields(fieldDefinitions)));
        }))
        .build());
  }
}
