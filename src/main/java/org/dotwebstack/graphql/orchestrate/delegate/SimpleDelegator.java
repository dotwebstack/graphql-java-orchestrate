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
import org.dotwebstack.graphql.orchestrate.transform.TransformContext;

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
    var transformedRequest = Lists.reverse(subschema.getTransforms())
        .stream()
        .reduce(request, (acc, transform) -> transform.transformRequest(acc), Request::merge);

    var operationDefinition = OperationDefinition.newOperationDefinition()
        .operation(OperationDefinition.Operation.QUERY)
        .selectionSet(transformedRequest.getSelectionSet())
        .build();

    var executionInput = ExecutionInput.newExecutionInput()
        .query(AstPrinter.printAst(operationDefinition))
        .build();

    return subschema.execute(executionInput)
        .thenApply(executionResult -> processResult(executionResult, transformedRequest.getContext()));
  }

  private Object processResult(ExecutionResult executionResult, TransformContext context) {
    Map<String, Object> resultData = executionResult.getData();

    var result = Result.newResult()
        .data(resultData.get(fieldName))
        .context(context)
        .build();

    for (var transform : subschema.getTransforms()) {
      result = transform.transformResult(result);
    }

    return result.getData();
  }
}
