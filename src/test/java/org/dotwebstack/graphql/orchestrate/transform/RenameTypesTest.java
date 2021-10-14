package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RenameTypesTest {

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
    var transformedRequest = transform.transformRequest(originalRequest);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ... on Brewery {name}}}"));
  }
}
