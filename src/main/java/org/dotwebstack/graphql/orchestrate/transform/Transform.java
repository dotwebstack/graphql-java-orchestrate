package org.dotwebstack.graphql.orchestrate.transform;

import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;
import java.util.Map;

public interface Transform {

  default GraphQLSchema transformSchema(GraphQLSchema schema) {
    return schema;
  }

  default OperationDefinition transformRequest(OperationDefinition operationDefinition, Map<String, Object> variables) {
    return operationDefinition;
  }

  default Object transformResult(Object data) {
    return data;
  }
}
