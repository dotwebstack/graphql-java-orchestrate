package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtil.fieldArguments;
import static org.dotwebstack.graphql.orchestrate.test.TestUtil.fieldDefinition;
import static org.dotwebstack.graphql.orchestrate.test.TestUtil.fieldType;
import static org.dotwebstack.graphql.orchestrate.test.TestUtil.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtil.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenameObjectFieldsTest {

  private GraphQLSchema schema;

  private RenameObjectFields transform;

  @BeforeEach
  void setUp() {
    schema = loadSchema("dbeerpedia");
    transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("name") ? "label" : fieldName);
  }

  @Test
  void transformSchema_RenamesField_UsingRenamer() {
    var transformedSchema = transform.transformSchema(schema);

    assertThat(fieldType(transformedSchema, "Brewery", "label"), is(fieldType(schema, "Brewery", "name")));
    assertThat(fieldArguments(transformedSchema, "Brewery", "label"), is(fieldArguments(schema, "Brewery", "name")));
    assertThat(fieldDefinition(transformedSchema, "Brewery", "identifier"),
        is(fieldDefinition(schema, "Brewery", "identifier")));
    assertThat(fieldDefinition(transformedSchema, "Brewery", "name"), is(nullValue()));
  }

  @Test
  void transformRequest_AddsAlias_ForRegularFields() {
    transform.transformSchema(schema);

    var request = parseQuery("{brewery(identifier:\"foo\") {identifier label}}");
    var transformedRequest = transform.transformRequest(request);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier label:name}}"));
  }

  @Test
  void transformRequest_AddsAlias_ForInlineFragmentFields() {
    transform.transformSchema(schema);

    var request = parseQuery("{brewery(identifier:\"foo\") {identifier ... on Brewery {label}}}");
    var transformedRequest = transform.transformRequest(request);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ... on Brewery {label:name}}}"));
  }
}
