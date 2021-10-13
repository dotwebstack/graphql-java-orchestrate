package org.dotwebstack.graphql.orchestrate.delegate;

import static graphql.language.Field.newField;
import static org.dotwebstack.graphql.orchestrate.test.Matchers.hasStringArgument;
import static org.dotwebstack.graphql.orchestrate.test.Matchers.hasZeroArguments;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.extractQueryField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import graphql.ExecutionInput;
import graphql.ExecutionResultImpl;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;
import org.dotwebstack.graphql.orchestrate.transform.Transform;
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
  void delegate_delegatesQueryWithoutArgs_whenNoArgsGiven() throws Exception {
    var delegator = buildDelegator("foo", "bar", null);
    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField.getName(), equalTo("foo"));
    assertThat(queryField, hasZeroArguments());
  }

  @Test
  void delegate_delegatesQueryWithArgs_whenArgsGiven() throws Exception {
    when(environment.getSource()).thenReturn(Map.of("key1", "val1"));

    ArgsFromEnvFunction argsFromEnv = env -> {
      Map<String, String> source = env.getSource();
      return List.of(new Argument("arg1", StringValue.of(source.get("key1"))));
    };

    var result = buildDelegator("foo", "bar", argsFromEnv).delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField.getName(), equalTo("foo"));
    assertThat(queryField, hasStringArgument("arg1", "val1"));
  }

  @Test
  void delegate_appliesRequestTransforms_whenGiven() throws Exception {
    when(subschema.getTransforms()).thenReturn(List.of(new Transform() {
      @Override
      public Request transformRequest(Request originalRequest) {
        return originalRequest.transform(builder -> builder.selectionSet(new SelectionSet(List.of(new Field("baz"))))
            .build());
      }
    }));

    var delegator = buildDelegator("foo", "bar", null);
    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField.getName(), equalTo("baz"));
    assertThat(queryField, hasZeroArguments());
  }

  @Test
  void delegate_appliesResponseTransforms_whenGiven() throws Exception {
    when(subschema.getTransforms()).thenReturn(List.of(new Transform() {
      @Override
      public Result transformResult(Result originalResult) {
        return originalResult.transform(builder -> builder.data("baz")
            .build());
      }
    }));

    var delegator = buildDelegator("foo", "bar", null);
    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("baz"));

    var queryField = extractQueryField(queryCaptor.getValue());
    assertThat(queryField.getName(), equalTo("foo"));
    assertThat(queryField, hasZeroArguments());
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
