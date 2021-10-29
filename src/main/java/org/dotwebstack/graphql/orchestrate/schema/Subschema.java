package org.dotwebstack.graphql.orchestrate.schema;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.dotwebstack.graphql.orchestrate.transform.Transform;

@Slf4j
@Getter
@Builder(builderMethodName = "newSubschema", toBuilder = true)
public class Subschema {

  @NonNull
  private final GraphQLSchema schema;

  private final Executor executor;

  private final Transform transform;

  public CompletableFuture<ExecutionResult> execute(ExecutionInput input) {
    LOG.debug("Executing query:\n{}", input.getQuery());

    if (executor != null) {
      return executor.execute(input);
    }

    var graphql = GraphQL.newGraphQL(schema)
        .build();

    return graphql.executeAsync(input);
  }

  public Subschema transform(@NonNull Consumer<SubschemaBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
