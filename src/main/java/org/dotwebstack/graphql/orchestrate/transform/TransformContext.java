package org.dotwebstack.graphql.orchestrate.transform;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.NonNull;

@Builder(builderMethodName = "newTransformContext", toBuilder = true)
public class TransformContext {

  @NonNull
  @Builder.Default
  private final ConcurrentMap<String, Object> contextMap = new ConcurrentHashMap<>();

  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) contextMap.get(key);
  }

  public TransformContext transform(@NonNull Consumer<TransformContextBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
