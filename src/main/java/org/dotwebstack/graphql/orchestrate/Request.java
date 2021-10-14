package org.dotwebstack.graphql.orchestrate;

import graphql.language.SelectionSet;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.transform.TransformContext;

@Getter
@Builder(builderMethodName = "newRequest", toBuilder = true)
public final class Request {

  @NonNull
  private final SelectionSet selectionSet;

  @NonNull
  @Builder.Default
  private final TransformContext context = TransformContext.newTransformContext()
      .build();

  public Request merge(Request request) {
    return transform(builder -> builder.selectionSet(request.getSelectionSet())
        .context(request.getContext()));
  }

  public Request transform(@NonNull Consumer<RequestBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
