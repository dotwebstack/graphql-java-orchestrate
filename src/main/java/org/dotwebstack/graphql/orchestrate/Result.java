package org.dotwebstack.graphql.orchestrate;

import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(builderMethodName = "newResult", toBuilder = true)
public final class Result {

  private final Object data;

  public Result transform(Function<Result.ResultBuilder, Result> transformer) {
    return transformer.apply(toBuilder());
  }
}
