package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
class RenameTypesTest {

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
  void transformRequest_replacesTypeName_ForInlineFragments() {
    var transform = new RenameTypes((typeName, typeDefinition) -> typeName.equals("Brewery") ? "Company" : typeName);

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier ... on Company {name}}}");

    Mockito.when(nextMock.apply(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(Result.newResult()
            .build()));

    transform.transform(originalRequest, nextMock);
    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ... on Brewery {name}}}"));
  }
}
