package org.dotwebstack.graphql.orchestrate.schema;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.exception.GraphqlJavaOrchestrateException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
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
    var body = Map.of("query", input.getQuery(), "variables", input.getVariables());

    return webClient.post()
        .uri(endpoint)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(body))
        .exchangeToMono(clientResponse -> clientResponse.bodyToMono(mapTypeRef))
        .map(RemoteExecutor::mapToResult)
        .toFuture();
  }

  private static ExecutionResult mapToResult(Map<String, Object> body) {
    if (body.containsKey("data")) {
      return ExecutionResultImpl.newExecutionResult()
          .data(body.get("data"))
          .build();
    }

    if (body.containsKey("status") && body.containsKey("detail")) {
      return ExecutionResultImpl.newExecutionResult()
          .addError(new ExceptionWhileDataFetching(ResultPath.rootPath(),
              new GraphqlJavaOrchestrateException(HttpStatus.valueOf(Integer.parseInt(body.get("status")
                  .toString())), body.get("detail")
                      .toString()),
              SourceLocation.EMPTY))
          .build();
    }

    return ExecutionResultImpl.newExecutionResult()
        .addError(new ExceptionWhileDataFetching(ResultPath.rootPath(),
            new GraphqlJavaOrchestrateException(HttpStatus.valueOf(500),
                "Something went wrong while orchestrating the request."),
            SourceLocation.EMPTY))
        .build();
  }
}
