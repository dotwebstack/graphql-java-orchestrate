package org.dotwebstack.graphql.orchestrate.delegate;

import static graphql.language.Field.newField;
import static org.dotwebstack.graphql.orchestrate.test.Matchers.hasStringArgument;
import static org.dotwebstack.graphql.orchestrate.test.Matchers.hasZeroArguments;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.extractQueryField;
import static org.dotwebstack.graphql.orchestrate.util.ValueUtils.scalarValueFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.language.Argument;
import graphql.schema.DataFetchingEnvironment;
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

  @Mock
  private DataFetchingEnvironment environment;

  @Captor
  private ArgumentCaptor<ExecutionInput> queryCaptor;

  @Test
  void delegate_DelegatesQueryWithoutArgs_WhenNoArgsGiven() throws Exception {
    var delegator = buildDelegator("foo", "bar", null);
    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField, hasZeroArguments());
  }

  @Test
  void delegate_DelegatesQueryWithArgs_WhenArgsGiven() throws Exception {
    when(environment.getSource()).thenReturn(Map.of("key1", "val1"));

    ArgsFromEnvFunction argsFromEnv = env -> {
      Map<String, Object> source = env.getSource();
      return List.of(new Argument("arg1", scalarValueFrom(source.get("key1"))));
    };

    var result = buildDelegator("foo", "bar", argsFromEnv).delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField, hasStringArgument("arg1", "val1"));
  }

  private SimpleDelegator buildDelegator(String fieldName, Object data, ArgsFromEnvFunction argsFromEnv) {
    when(subschema.execute(queryCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(ExecutionResultImpl.newExecutionResult()
            .data(Map.of(fieldName, data))
            .build()));

    when(environment.getField()).thenReturn(newField(fieldName).build());

    var delegatorBuilder = SimpleDelegator.builder()
        .subschema(subschema)
        .fieldName(fieldName);

    if (argsFromEnv != null) {
      delegatorBuilder.argsFromEnv(argsFromEnv);
    }

    return delegatorBuilder.build();
  }
}
