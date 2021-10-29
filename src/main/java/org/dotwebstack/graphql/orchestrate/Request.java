package org.dotwebstack.graphql.orchestrate;

import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder(builderMethodName = "newRequest", toBuilder = true)
public final class Request {

  @NonNull
  private final SelectionSet selectionSet;

  @NonNull
  @Builder.Default
  private final List<VariableDefinition> variableDefinitions = List.of();

  @NonNull
  @Builder.Default
  private final Map<String, Object> variables = Map.of();

  public Request transform(@NonNull Consumer<RequestBuilder> builderConsumer) {
    var builder = toBuilder();
    builderConsumer.accept(builder);
    return builder.build();
  }
}
