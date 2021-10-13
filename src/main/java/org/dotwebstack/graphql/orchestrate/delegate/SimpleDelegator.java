package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.com.google.common.collect.Lists;
import graphql.language.AstPrinter;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.NonNull;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

@Builder
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
            .arguments(argsFromEnv.apply(environment)));

    var request = Request.newRequest()
        .selectionSet(new SelectionSet(List.of(rootField)))
        .build();

    // Apply request transforms in reverse order
    for (var transform : Lists.reverse(subschema.getTransforms())) {
      request = transform.transformRequest(request);
    }

    var operationDefinition = OperationDefinition.newOperationDefinition()
        .operation(OperationDefinition.Operation.QUERY)
        .selectionSet(request.getSelectionSet())
        .build();

    var executionInput = ExecutionInput.newExecutionInput()
        .query(AstPrinter.printAst(operationDefinition))
        .build();

    return subschema.execute(executionInput)
        .thenApply(this::processResult);
  }

  private Object processResult(ExecutionResult executionResult) {
    Map<String, Object> resultData = executionResult.getData();

    var result = Result.newResult()
        .data(resultData.get(fieldName))
        .build();

    for (var transform : subschema.getTransforms()) {
      result = transform.transformResult(result);
    }

    return result.getData();
  }
}
