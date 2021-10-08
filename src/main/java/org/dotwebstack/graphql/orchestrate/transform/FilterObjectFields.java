package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.stream.Collectors;
import lombok.NonNull;

public class FilterObjectFields implements Transform {

  private final ObjectFieldFilter filter;

  public FilterObjectFields(@NonNull ObjectFieldFilter filter) {
    this.filter = filter;
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema) {
    var typeVisitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType,
          TraverserContext<GraphQLSchemaElement> context) {
        var fieldDefinitions = objectType.getFieldDefinitions()
            .stream()
            .filter(fieldDefinition -> filterField(objectType, fieldDefinition))
            .collect(Collectors.toList());

        if (fieldDefinitions.isEmpty()) {
          throw new IllegalStateException("Object types must contain at least 1 field.");
        }

        return changeNode(context, objectType.transform(builder -> builder.replaceFields(fieldDefinitions)));
      }
    };

    return SchemaTransformer.transformSchema(originalSchema, typeVisitor);
  }

  private boolean filterField(GraphQLObjectType objectType, GraphQLFieldDefinition fieldDefinition) {
    return filter.apply(objectType.getName(), fieldDefinition.getName(), fieldDefinition);
  }
}
