package org.dotwebstack.graphql.orchestrate.schema;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.transform.Transform;

@Builder
@Getter
public class Subschema {

  @NonNull
  private final GraphQLSchema schema;

  private final Executor executor;

  private final List<Transform> transforms = List.of();

  public CompletableFuture<ExecutionResult> execute(ExecutionInput input) {
    if (executor != null) {
      return executor.execute(input);
    }

    var graphql = GraphQL.newGraphQL(schema)
        .build();

    return graphql.executeAsync(input);
  }
}
