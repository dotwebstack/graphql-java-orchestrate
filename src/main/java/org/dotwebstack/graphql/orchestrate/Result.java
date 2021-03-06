package org.dotwebstack.graphql.orchestrate;

import java.util.Map;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder(builderMethodName = "newResult", toBuilder = true)
public final class Result {

  private final Map<String, Object> data;

  public Result transform(@NonNull Consumer<ResultBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
