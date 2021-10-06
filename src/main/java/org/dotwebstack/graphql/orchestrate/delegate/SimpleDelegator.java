package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.ExecutionResult;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class SimpleDelegator extends AbstractDelegator {

  @NonNull
  @Builder.Default
  private final ArgsFromEnvFunction argsFromEnv = environment -> List.of();

  public CompletableFuture<Object> delegate(DataFetchingEnvironment environment) {
    var rootField = environment.getField()
        .transform(builder -> builder.name(fieldName)
            .arguments(argsFromEnv.apply(environment)));

    var query = buildQuery(rootField);

    return subschema.execute(query)
        .thenApply(this::processResult);
  }

  private Object processResult(ExecutionResult result) {
    Map<String, Object> data = result.getData();

    if (data == null) {
      return null;
    }

    return data.get(fieldName);
  }
}
