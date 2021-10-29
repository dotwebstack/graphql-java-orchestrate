package org.dotwebstack.graphql.orchestrate.delegate;

import static org.dotwebstack.graphql.orchestrate.test.Matchers.hasStringArgument;
import static org.dotwebstack.graphql.orchestrate.test.Matchers.hasZeroArguments;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.extractQueryField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.execution.MergedField;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimpleDelegatorTest {

  @Mock
  private Subschema subschema;

  @Captor
  private ArgumentCaptor<ExecutionInput> queryCaptor;

  @Test
  void delegate_delegatesQueryWithoutArgs_whenNoArgsGiven() throws Exception {
    var delegator = createDelegator(null);
    var environment = createEnvironment(null);

    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField.getName(), equalTo("foo"));
    assertThat(queryField, hasZeroArguments());
  }

  @Test
  void delegate_delegatesQueryWithArgs_whenArgsGiven() throws Exception {
    var environment = createEnvironment(Map.of("key1", "val1"));

    ArgsFromEnvFunction argsFromEnv = env -> {
      Map<String, String> source = env.getSource();
      return List.of(new Argument("arg1", StringValue.of(source.get("key1"))));
    };

    var result = createDelegator(argsFromEnv).delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField.getName(), equalTo("foo"));
    assertThat(queryField, hasStringArgument("arg1", "val1"));
  }

  private SimpleDelegator createDelegator(ArgsFromEnvFunction argsFromEnv) {
    when(subschema.execute(queryCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(ExecutionResultImpl.newExecutionResult()
            .data(Map.of("foo", "bar"))
            .build()));

    var delegatorBuilder = SimpleDelegator.newDelegator()
        .subschema(subschema)
        .fieldName("foo");

    if (argsFromEnv != null) {
      delegatorBuilder.argsFromEnv(argsFromEnv);
    }

    return delegatorBuilder.build();
  }

  private DataFetchingEnvironment createEnvironment(Object source) {
    var field = Field.newField("brewery")
        .arguments(List.of(new Argument("identificatie", StringValue.of("foo"))))
        .selectionSet(SelectionSet.newSelectionSet()
            .selection(new Field("name"))
            .build())
        .build();

    return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
        .operationDefinition(OperationDefinition.newOperationDefinition()
            .operation(OperationDefinition.Operation.QUERY)
            .variableDefinitions(List.of())
            .build())
        .mergedField(MergedField.newMergedField()
            .addField(field)
            .build())
        .source(source)
        .build();
  }
}
