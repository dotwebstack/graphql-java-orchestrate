package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.language.Argument;
import graphql.language.AstPrinter;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

@Slf4j
@Builder(builderMethodName = "newDelegator")
public class SimpleDelegator implements Delegator {

  @NonNull
  private final Subschema subschema;

  @NonNull
  private final String fieldName;

  @NonNull
  @Builder.Default
  private final ArgsFromEnvFunction argsFromEnv = environment -> List.of();

  public CompletableFuture<Object> delegate(DataFetchingEnvironment environment) {
    var rootField = environment.getField()
        .transform(builder -> builder.name(fieldName)
            .arguments(mergeArguments(environment)));

    var originalRequest = Request.newRequest()
        .selectionSet(new SelectionSet(List.of(rootField)))
        .variableDefinitions(environment.getOperationDefinition()
            .getVariableDefinitions())
        .variables(environment.getVariables())
        .build();

    return Optional.ofNullable(subschema.getTransform())
        .map(transform -> transform.transform(originalRequest, this::delegateRequest))
        .orElseGet(() -> this.delegateRequest(originalRequest))
        .thenApply(result -> result.getData()
            .get(fieldName));
  }

  private List<Argument> mergeArguments(DataFetchingEnvironment environment) {
    var passedArguments = environment.getField()
        .getArguments();

    var newArguments = argsFromEnv.apply(environment);

    return Stream.concat(passedArguments.stream(), newArguments.stream())
        .collect(Collectors.toList());
  }

  private CompletableFuture<Result> delegateRequest(Request request) {
    var operationDefinition = OperationDefinition.newOperationDefinition()
        .operation(OperationDefinition.Operation.QUERY)
        .selectionSet(request.getSelectionSet())
        .variableDefinitions(request.getVariableDefinitions())
        .build();

    var executionInput = ExecutionInput.newExecutionInput()
        .query(AstPrinter.printAst(operationDefinition))
        .variables(request.getVariables())
        .build();

    return subschema.execute(executionInput)
        .thenApply(this::mapResult);
  }

  private Result mapResult(ExecutionResult executionResult) {
    return Result.newResult()
        .data(executionResult.getData())
        .build();
  }
}
