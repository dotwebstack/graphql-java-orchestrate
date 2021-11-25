package org.dotwebstack.graphql.orchestrate.transform;

import graphql.schema.GraphQLSchema;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;

public abstract class AbstractTransform implements Transform {

  @Override
  public Transform pipe(@NonNull Transform nextTransform) {
    var outerTransform = this;

    return new AbstractTransform() {
      @Override
      public GraphQLSchema transformSchema(GraphQLSchema originalSchema, TransformContext context) {
        return nextTransform.transformSchema(outerTransform.transformSchema(originalSchema, context), context);
      }

      @Override
      public CompletableFuture<Result> transform(Request originalRequest,
          Function<Request, CompletableFuture<Result>> next) {
        return nextTransform.transform(originalRequest,
            transformedRequest -> outerTransform.transform(transformedRequest, next));
      }
    };
  }

  @Override
  public GraphQLSchema transformSchema(@NonNull GraphQLSchema originalSchema, @NonNull TransformContext context) {
    return originalSchema;
  }

  @Override
  public CompletableFuture<Result> transform(@NonNull Request originalRequest,
      @NonNull Function<Request, CompletableFuture<Result>> next) {
    return next.apply(originalRequest);
  }
}
