package org.dotwebstack.graphql.orchestrate.wrap;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import graphql.ExecutionResultImpl;
import graphql.execution.MergedField;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dotwebstack.graphql.orchestrate.schema.Executor;
import org.dotwebstack.graphql.orchestrate.schema.Subschema;
import org.dotwebstack.graphql.orchestrate.transform.Transform;
import org.dotwebstack.graphql.orchestrate.transform.TransformContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaWrapperTest {

  @Mock
  private Transform transform;

  @Captor
  private ArgumentCaptor<TransformContext> contextCaptor;

  @Mock
  private Executor executor;

  private GraphQLSchema originalSchema;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void wrap_appliesSchemaTransforms_onGivenSubschema() {
    var transformedSchema = originalSchema.transform(GraphQLSchema.Builder::build);
    when(transform.transformSchema(any(), contextCaptor.capture())).thenReturn(transformedSchema);

    var subschema = Subschema.newSubschema()
        .schema(originalSchema)
        .transform(transform)
        .build();

    var wrappedSchema = SchemaWrapper.wrap(subschema);

    assertThat(wrappedSchema, Matchers.is(transformedSchema));
    var context = contextCaptor.getValue();
    assertThat(context.getSubschema(), is(subschema));
  }

  @Test
  void wrap_delegatesDataFetcher_usingGivenExecutor() throws Exception {
    var subschema = Subschema.newSubschema()
        .schema(originalSchema)
        .executor(executor)
        .build();

    var expectedResult = ExecutionResultImpl.newExecutionResult()
        .data(Map.of("brewery", "bar"))
        .build();

    when(executor.execute(any())).thenReturn(CompletableFuture.completedFuture(expectedResult));

    var environment = createEnvironment();
    var wrappedSchema = SchemaWrapper.wrap(subschema);

    var dataFetcher = wrappedSchema.getCodeRegistry()
        .getDataFetcher(wrappedSchema.getQueryType(), wrappedSchema.getQueryType()
            .getField("brewery"));

    var result = dataFetcher.get(environment);

    assertThat(result, equalTo(result));
  }

  private DataFetchingEnvironment createEnvironment() {
    var field = Field.newField("brewery")
        .arguments(List.of(new Argument("identifier", StringValue.of("foo"))))
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
        .build();
  }
}
