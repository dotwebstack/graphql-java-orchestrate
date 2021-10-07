package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLSchema;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public interface Transform {

  default GraphQLSchema transformSchema(GraphQLSchema schema) {
    return schema;
  }

  default Request transformRequest(Request originalRequest) {
    return originalRequest;
  }

  default Result transformResult(Result originalResult) {
    return originalResult;
  }
}
