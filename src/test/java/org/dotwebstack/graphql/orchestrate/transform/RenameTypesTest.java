package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenameTypesTest {

  private GraphQLSchema originalSchema;

  private RenameTypes transform;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
    transform = new RenameTypes((typeName, typeDefinition) -> typeName.equals("Brewery") ? "Company" : typeName);
  }

  @Test
  void transformRequest_replacesTypeName_ForInlineFragments() {
    transform.transformSchema(originalSchema);

    var request = parseQuery("{brewery(identifier:\"foo\") {identifier ... on Company {name}}}");
    var transformedRequest = transform.transformRequest(request);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ... on Brewery {name}}}"));
  }
}
