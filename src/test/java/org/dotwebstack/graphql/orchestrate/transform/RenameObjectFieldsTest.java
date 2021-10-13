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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenameObjectFieldsTest {

  private GraphQLSchema originalSchema;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void transformSchema_RenamesField_UsingRenamer() {
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
  void transformRequest_AddsAlias_ForRootField() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("brewery") ? "company" : fieldName);

    transform.transformSchema(originalSchema);

    var request = parseQuery("{company(identifier:\"foo\") {identifier name}}");
    var transformedRequest = transform.transformRequest(request);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{company:brewery(identifier:\"foo\") {identifier name}}"));
  }

  @Test
  void transformRequest_AddsAlias_ForRegularFields() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("name") ? "label" : fieldName);

    transform.transformSchema(originalSchema);

    var request = parseQuery("{brewery(identifier:\"foo\") {identifier label}}");
    var transformedRequest = transform.transformRequest(request);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier label:name}}"));
  }

  @Test
  void transformRequest_AddsAlias_ForInlineFragmentFields() {
    var transform = new RenameObjectFields(
        (typeName, fieldName, fieldDefinition) -> fieldName.equals("name") ? "label" : fieldName);

    transform.transformSchema(originalSchema);

    var request = parseQuery("{brewery(identifier:\"foo\") {identifier ... on Brewery {label}}}");
    var transformedRequest = transform.transformRequest(request);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ... on Brewery {label:name}}}"));
  }
}
