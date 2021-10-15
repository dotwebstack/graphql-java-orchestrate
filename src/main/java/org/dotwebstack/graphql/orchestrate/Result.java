package org.dotwebstack.graphql.orchestrate;

import java.util.function.Consumer;
import lombok.Builder;
import lombok.NonNull;

@Builder(builderMethodName = "newResult", toBuilder = true)
public final class Result {

  private final Object data;

  @SuppressWarnings("unchecked")
  public <T> T getData() {
    return (T) data;
  }

  public Result transform(@NonNull Consumer<ResultBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
