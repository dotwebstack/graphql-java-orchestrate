package org.dotwebstack.graphql.orchestrate;

import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.transform.TransformContext;

@Getter
@Builder(builderMethodName = "newResult", toBuilder = true)
public final class Result {

  private final Object data;

  @NonNull
  @Builder.Default
  private final TransformContext context = TransformContext.newTransformContext()
      .build();

  public Result transform(@NonNull Consumer<ResultBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
