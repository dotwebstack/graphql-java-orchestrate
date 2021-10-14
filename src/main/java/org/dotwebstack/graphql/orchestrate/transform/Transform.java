package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLSchema;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public interface Transform {

  default GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema) {
    return originalSchema;
  }

  default Request transformRequest(@NonNull Request originalRequest) {
    return originalRequest;
  }

  default Result transformResult(@NonNull Result originalResult) {
    return originalResult;
  }
}
