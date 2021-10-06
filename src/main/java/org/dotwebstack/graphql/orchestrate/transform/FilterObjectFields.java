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

public class FilterObjectFields implements Transform {

  private final ObjectFieldFilter filter;

  public FilterObjectFields(ObjectFieldFilter filter) {
    this.filter = filter;
  }

  @Override
  public GraphQLSchema transformSchema(GraphQLSchema schema) {
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

    return SchemaTransformer.transformSchema(schema, typeVisitor);
  }

  private boolean filterField(GraphQLObjectType objectType, GraphQLFieldDefinition fieldDefinition) {
    return filter.apply(objectType.getName(), fieldDefinition.getName(), fieldDefinition);
  }
}
