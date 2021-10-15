package org.dotwebstack.graphql.orchestrate.wrap;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import graphql.ExecutionResultImpl;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dotwebstack.graphql.orchestrate.schema.Executor;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;
import org.dotwebstack.graphql.orchestrate.transform.RenameTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaWrapperTest {

  @Mock
  private Executor executor;

  @Mock
  private DataFetchingEnvironment environment;

  private GraphQLSchema originalSchema;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void wrap_appliesSchemaTransforms_onGivenSubschema() {
    var transform = new RenameTypes((typeName, typeDefinition) -> typeName.equals("Brewery") ? "Company" : typeName);

    var subschema = Subschema.builder()
        .schema(originalSchema)
        .transform(transform)
        .build();

    var wrappedSchema = SchemaWrapper.wrap(subschema);

    assertThat(wrappedSchema.containsType("Brewery"), is(false));
    assertThat(wrappedSchema.containsType("Company"), is(true));
  }

  @Test
  void wrap_delegatesDataFetcher_usingGivenExecutor() throws Exception {
    var subschema = Subschema.builder()
        .schema(originalSchema)
        .executor(executor)
        .build();

    var expectedResult = ExecutionResultImpl.newExecutionResult()
        .data(Map.of("brewery", "bar"))
        .build();

    when(executor.execute(any())).thenReturn(CompletableFuture.completedFuture(expectedResult));

    when(environment.getField()).thenReturn(Field.newField("brewery")
        .arguments(List.of(new Argument("identificatie", StringValue.of("foo"))))
        .selectionSet(SelectionSet.newSelectionSet()
            .selection(new Field("name"))
            .build())
        .build());

    var wrappedSchema = SchemaWrapper.wrap(subschema);

    var dataFetcher = wrappedSchema.getCodeRegistry()
        .getDataFetcher(wrappedSchema.getQueryType(), wrappedSchema.getQueryType()
            .getField("brewery"));

    var result = dataFetcher.get(environment);

    assertThat(result, equalTo(result));
  }
}