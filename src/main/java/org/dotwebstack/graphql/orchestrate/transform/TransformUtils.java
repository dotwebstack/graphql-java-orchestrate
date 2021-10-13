package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.analysis.QueryTransformer.newQueryTransformer;
import static graphql.schema.SchemaTransformer.transformSchema;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.util.Map;
import org.dotwebstack.graphql.orchestrate.Request;

class TransformUtils {

  private TransformUtils() {}

  public static GraphQLSchema mapSchema(GraphQLSchema schema, SchemaMapping mapping) {
    var typeVisitor = new GraphQLTypeVisitorStub() {
      @Override
      public TraversalControl visitGraphQLObjectType(GraphQLObjectType node,
          TraverserContext<GraphQLSchemaElement> context) {
        return mapping.getObjectType()
            .apply(node, context);
      }

      @Override
      public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node,
          TraverserContext<GraphQLSchemaElement> context) {
        return mapping.getInterfaceType()
            .apply(node, context);
      }
    };

    return transformSchema(schema, typeVisitor);
  }

  public static Request mapRequest(Request request, GraphQLSchema schema, RequestMapping mapping) {
    var queryTransformer = newQueryTransformer().schema(schema)
        .root(request.getSelectionSet())
        .rootParentType(schema.getQueryType())
        .fragmentsByName(Map.of())
        .variables(Map.of())
        .build();

    var queryVisitor = new QueryVisitorStub() {
      @Override
      public TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment environment) {
        return mapping.getField()
            .apply(environment);
      }
    };

    var newSelectionSet = (SelectionSet) queryTransformer.transform(queryVisitor);

    return request.transform(builder -> builder.selectionSet(newSelectionSet)
        .build());
  }
}
