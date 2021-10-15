package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLSchema;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public interface Transform {

  Transform pipe(Transform transform);

  GraphQLSchema transformSchema(GraphQLSchema originalSchema);

  CompletableFuture<Result> transform(Request originalRequest, Function<Request, CompletableFuture<Result>> next);
}
