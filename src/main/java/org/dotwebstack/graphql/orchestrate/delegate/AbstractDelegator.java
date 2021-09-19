package org.dotwebstack.graphql.orchestrate.delegate;

import graphql.ExecutionInput;
import graphql.com.google.common.collect.Lists;
import graphql.language.AstPrinter;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;

@SuperBuilder
@Getter
abstract class AbstractDelegator implements Delegator {

  @NonNull
  protected final Subschema subschema;

  @NonNull
  protected final String fieldName;

  protected ExecutionInput buildQuery(Field rootField) {
    var operationDefinition = OperationDefinition.newOperationDefinition()
        .operation(OperationDefinition.Operation.QUERY)
        .selectionSet(new SelectionSet(List.of(rootField)))
        .build();

    // Apply document transforms in reverse order
    for (var transform : Lists.reverse(subschema.getTransforms())) {
      operationDefinition = transform.transformRequest(operationDefinition, Map.of());
    }

    return ExecutionInput.newExecutionInput()
        .query(AstPrinter.printAst(operationDefinition))
        .build();
  }
}
