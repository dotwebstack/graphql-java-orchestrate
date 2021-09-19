package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationState;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;

@SuperBuilder
@Getter
public class BatchDelegator extends AbstractDelegator {

  @NonNull
  private final KeyFromEnvFunction keyFromEnv;

  @NonNull
  private final ArgsFromKeysFunction argsFromKeys;

  private final String dataLoaderKey;

  private BatchDelegator(BatchDelegatorBuilder<?, ?> builder) {
    super(builder);
    keyFromEnv = builder.keyFromEnv;
    argsFromKeys = builder.argsFromKeys;
    dataLoaderKey = UUID.randomUUID().toString();
  }

  public CompletableFuture<Object> delegate(DataFetchingEnvironment environment) {
    var dataLoaderRegistry = environment.getDataLoaderRegistry();

    if (DataLoaderDispatcherInstrumentationState.EMPTY_DATALOADER_REGISTRY.equals(dataLoaderRegistry)) {
      throw new IllegalStateException("Data loader registry must be passed to ExecutionInput.");
    }

    // The first delegation call registers the dataloader
    dataLoaderRegistry.computeIfAbsent(dataLoaderKey, k ->
        buildDataLoader(environment.getField()));

    return environment.getDataLoader(dataLoaderKey)
        .load(keyFromEnv.apply(environment));
  }

  private DataLoader<Object, Object> buildDataLoader(Field field) {
    return DataLoaderFactory.newDataLoader(keys -> {
      var rootField = field.transform(builder -> builder
              .name(fieldName)
              .arguments(argsFromKeys.apply(keys)));

      var query = buildQuery(rootField);

      return subschema.executeQuery(query)
          .thenApply(this::processResult);
    });
  }

  private List<Object> processResult(ExecutionResult result) {
    Map<String, List<Object>> data = result.getData();

    if (data == null || data.get(fieldName) == null) {
      throw new IllegalStateException("Batch query did not yield any results.");
    }

    return data.get(fieldName);
  }
}
