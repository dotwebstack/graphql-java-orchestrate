package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.fieldArguments;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.fieldDefinition;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.fieldType;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenameObjectFieldsTest {

  @Mock
  private Function<Request, CompletableFuture<Result>> nextMock;

  @Captor
  private ArgumentCaptor<Request> requestCaptor;

  private static GraphQLSchema originalSchema;

  @BeforeAll
  static void beforeAll() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void transformSchema_renamesField_UsingRenamer() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("name") ? "label" : fieldName);

    var transformedSchema = transform.transformSchema(originalSchema);

    assertThat(fieldType(transformedSchema, "Brewery", "label"), is(fieldType(originalSchema, "Brewery", "name")));
    assertThat(fieldArguments(transformedSchema, "Brewery", "label"),
        is(fieldArguments(originalSchema, "Brewery", "name")));
    assertThat(fieldDefinition(transformedSchema, "Brewery", "identifier"),
        is(fieldDefinition(originalSchema, "Brewery", "identifier")));
    assertThat(fieldDefinition(transformedSchema, "Brewery", "name"), is(nullValue()));
  }

  @Test
  void transformRequest_addsAlias_ForRootField() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("brewery") ? "company" : fieldName);

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{company(identifier:\"foo\") {identifier name}}");

    Mockito.when(nextMock.apply(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(Result.newResult()
            .build()));

    transform.transform(originalRequest, nextMock);
    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{company:brewery(identifier:\"foo\") {identifier name}}"));
  }

  @Test
  void transformRequest_addsAlias_ForNestedField() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("name") ? "label" : fieldName);

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier label}}");

    Mockito.when(nextMock.apply(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(Result.newResult()
            .build()));

    transform.transform(originalRequest, nextMock);
    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier label:name}}"));
  }

  @Test
  void transformRequest_addsAlias_ForInlineFragmentField() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("name") ? "label" : fieldName);

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier ... on Brewery {label}}}");

    Mockito.when(nextMock.apply(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(Result.newResult()
            .build()));

    transform.transform(originalRequest, nextMock);
    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ... on Brewery {label:name}}}"));
  }
}
