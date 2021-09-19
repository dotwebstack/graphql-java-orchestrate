package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenameObjectFieldsTest {

  private GraphQLSchema schema;

  private RenameObjectFields transform;

  @BeforeEach
  void setUp() {
    schema = loadSchema("dbeerpedia");
    transform = new RenameObjectFields((typeName, fieldName, fieldDefinition) ->
        fieldName.equals("name") ? "label" : fieldName);
  }

  @Test
  void transformSchema_RenamesField_UsingRenamer() {
    var transformedSchema = transform.transformSchema(schema);

    assertThat(fieldType(transformedSchema, "Brewery", "label"),
        is(fieldType(schema, "Brewery", "name")));
    assertThat(fieldArguments(transformedSchema, "Brewery", "label"),
        is(fieldArguments(schema, "Brewery", "name")));
    assertThat(fieldDefinition(transformedSchema, "Brewery", "identifier"),
        is(fieldDefinition(schema, "Brewery", "identifier")));
    assertThat(fieldDefinition(transformedSchema, "Brewery", "name"),
        is(nullValue()));
  }

  @Test
  void transformRequest_AddsAlias_ForRegularFields() {
    transform.transformSchema(schema);

    var request = parseQuery("query {brewery(identifier:\"foo\") {identifier label}}");
    var transformedDocument = transform.transformRequest(request, Map.of());

    assertThat(AstPrinter.printAstCompact(transformedDocument),
        equalTo("query {brewery(identifier:\"foo\") {identifier label:name}}"));
  }

  @Test
  void transformRequest_AddsAlias_ForInlineFragmentFields() {
    transform.transformSchema(schema);

    var request = parseQuery("query {brewery(identifier:\"foo\") {identifier ... on Brewery {label}}}");
    var transformedDocument = transform.transformRequest(request, Map.of());

    assertThat(AstPrinter.printAstCompact(transformedDocument),
        equalTo("query {brewery(identifier:\"foo\") {identifier ... on Brewery {label:name}}}"));
  }
}
