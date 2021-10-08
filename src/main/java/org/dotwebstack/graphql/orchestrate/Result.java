package org.dotwebstack.graphql.orchestrate;

import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder(builderMethodName = "newResult", toBuilder = true)
public final class Result {

  @NonNull
  private final Object data;

  public Result transform(@NonNull Function<ResultBuilder, Result> transformer) {
    return transformer.apply(toBuilder());
  }
}
