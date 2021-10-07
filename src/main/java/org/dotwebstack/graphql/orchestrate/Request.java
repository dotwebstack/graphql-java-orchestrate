package org.dotwebstack.graphql.orchestrate;

import graphql.language.SelectionSet;
import java.util.function.Function;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(builderMethodName = "newRequest", toBuilder = true)
public final class Request {

  private final SelectionSet selectionSet;

  public Request transform(Function<RequestBuilder, Request> transformer) {
    return transformer.apply(toBuilder());
  }
}
