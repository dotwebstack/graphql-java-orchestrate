package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FilterObjectFieldsTest {

  private static GraphQLSchema originalSchema;

  @BeforeAll
  static void beforeAll() {
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

    assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema));
  }
}
