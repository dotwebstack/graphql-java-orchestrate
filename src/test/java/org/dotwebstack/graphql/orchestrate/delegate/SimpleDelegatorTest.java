package org.dotwebstack.graphql.orchestrate.delegate;

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
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    var environment = createEnvironment(createField(List.of()), null, List.of(), Map.of());

    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var executionInput = queryCaptor.getValue();
    assertThat(executionInput.getVariables(), equalTo(Map.of()));

    var query = executionInput.getQuery();
    assertThat(query, equalTo("query {\n  foo {\n    name\n  }\n}"));
  }

  @Test
  void delegate_delegatesQueryWithArgs_whenArgsPresent() throws Exception {
    var environment = createEnvironment(createField(List.of()), Map.of("key1", "val1"), List.of(), Map.of());

    ArgsFromEnvFunction argsFromEnv = env -> {
      Map<String, String> source = env.getSource();
      return List.of(new Argument("arg1", StringValue.of(source.get("key1"))));
    };

    var result = createDelegator(argsFromEnv).delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var executionInput = queryCaptor.getValue();
    assertThat(executionInput.getVariables(), equalTo(Map.of()));

    var query = executionInput.getQuery();
    assertThat(query, equalTo("query {\n  foo(arg1: \"val1\") {\n    name\n  }\n}"));
  }

  @Test
  void delegate_delegatesQueryWithVars_whenVarsPresent() throws Exception {
    var arguments = List.of(new Argument("identifier", new VariableReference("identifier")));
    var variableDefinitions = List.of(new VariableDefinition("identifier", new TypeName("String")));
    Map<String, Object> variables = Map.of("identifier", "foo");
    var environment = createEnvironment(createField(arguments), null, variableDefinitions, variables);

    ArgsFromEnvFunction argsFromEnv = env -> env.getField()
        .getArguments();

    var delegator = createDelegator(argsFromEnv);
    var result = delegator.delegate(environment);

    assertThat(result.isDone(), equalTo(true));
    assertThat(result.get(), equalTo("bar"));

    var executionInput = queryCaptor.getValue();
    assertThat(executionInput.getVariables(), equalTo(variables));

    var query = executionInput.getQuery();
    assertThat(query, equalTo(
        "query ($identifier: String) {\n" + "  foo(identifier: $identifier) {\n" + "    name\n" + "  }\n" + "}"));
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

  private Field createField(List<Argument> arguments) {
    return Field.newField("brewery")
        .arguments(arguments)
        .selectionSet(SelectionSet.newSelectionSet()
            .selection(new Field("name"))
            .build())
        .build();
  }

  private DataFetchingEnvironment createEnvironment(Field field, Object source,
      List<VariableDefinition> variableDefinitions, Map<String, Object> variables) {
    return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
        .operationDefinition(OperationDefinition.newOperationDefinition()
            .operation(OperationDefinition.Operation.QUERY)
            .variableDefinitions(variableDefinitions)
            .build())
        .mergedField(MergedField.newMergedField()
            .addField(field)
            .build())
        .source(source)
        .variables(Optional.ofNullable(variables)
            .orElse(Map.of()))
        .build();
  }
}
