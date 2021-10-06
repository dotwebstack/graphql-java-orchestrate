package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtil.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtil.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenameTypesTest {

  private GraphQLSchema schema;

  private RenameTypes transform;

  @BeforeEach
  void setUp() {
    schema = loadSchema("dbeerpedia");
    transform = new RenameTypes((typeName, typeDefinition) -> typeName.equals("Brewery") ? "Company" : typeName);
  }

  @Test
  void transformRequest_AddsAlias_ForInlineFragmentFields() {
    transform.transformSchema(schema);

    var request = parseQuery("query {brewery(identifier:\"foo\") {identifier ... on Company {name}}}");
    var transformedDocument = transform.transformRequest(request, Map.of());

    assertThat(AstPrinter.printAstCompact(transformedDocument),
        equalTo("query {brewery(identifier:\"foo\") {identifier ... on Brewery {name}}}"));
  }
}
