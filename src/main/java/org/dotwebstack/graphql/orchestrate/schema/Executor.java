package org.dotwebstack.graphql.orchestrate.schema;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import java.util.concurrent.CompletableFuture;

public interface Executor {

  CompletableFuture<ExecutionResult> execute(ExecutionInput input);
}
