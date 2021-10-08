package org.dotwebstack.graphql.orchestrate;

import graphql.language.SelectionSet;
import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder(builderMethodName = "newRequest", toBuilder = true)
public final class Request {

  @NonNull
  private final SelectionSet selectionSet;

  public Request transform(@NonNull Function<RequestBuilder, Request> transformer) {
    return transformer.apply(toBuilder());
  }
}
