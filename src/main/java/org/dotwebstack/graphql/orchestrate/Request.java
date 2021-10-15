package org.dotwebstack.graphql.orchestrate;

import graphql.language.SelectionSet;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder(builderMethodName = "newRequest", toBuilder = true)
public final class Request {

  @NonNull
  private final SelectionSet selectionSet;

  public Request transform(@NonNull Consumer<RequestBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
