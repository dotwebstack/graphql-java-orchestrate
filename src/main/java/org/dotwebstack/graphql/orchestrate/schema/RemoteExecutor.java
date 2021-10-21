package org.dotwebstack.graphql.orchestrate.schema;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Builder(builderMethodName = "newExecutor")
public class RemoteExecutor implements Executor {

  @NonNull
  private final WebClient webClient;

  @NonNull
  private final URI endpoint;

  public CompletableFuture<ExecutionResult> execute(ExecutionInput input) {
    var mapTypeRef = new ParameterizedTypeReference<Map<String, Object>>() {};

    return webClient.post()
        .uri(endpoint)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(Map.of("query", input.getQuery())))
        .retrieve()
        .bodyToMono(mapTypeRef)
        .map(RemoteExecutor::mapToResult)
        .toFuture();
  }

  private static ExecutionResult mapToResult(Map<String, Object> body) {
    return ExecutionResultImpl.newExecutionResult()
        .data(body.get("data"))
        .build();
  }
}
