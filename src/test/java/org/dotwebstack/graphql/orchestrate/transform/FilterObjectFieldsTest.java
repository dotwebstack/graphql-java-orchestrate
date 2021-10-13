package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilterObjectFieldsTest {

  private GraphQLSchema originalSchema;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void transformSchema_removedField_whenPredicateMatches() {
    var transform = new FilterObjectFields(
        (typeName, fieldName, fieldDefinition) -> !(typeName.equals("Brewery") && fieldName.equals("name")));

    var transformedSchema = transform.transformSchema(originalSchema);
    var breweryType = transformedSchema.getObjectType("Brewery");

    assertThat(breweryType.getFieldDefinition("identifier"), notNullValue());
    assertThat(breweryType.getFieldDefinition("name"), nullValue());
  }

  @Test
  void transformSchema_throwsException_whenPredicateMatchesAllFields() {
    var transform = new FilterObjectFields((typeName, fieldName, fieldDefinition) -> !typeName.equals("Brewery"));

    Assertions.assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema));
  }
}
