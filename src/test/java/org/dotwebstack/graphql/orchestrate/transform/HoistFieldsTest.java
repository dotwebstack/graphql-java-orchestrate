package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.schema.GraphQLSchema;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HoistFieldsTest {

  private GraphQLSchema originalSchema;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void constructor_throwsException_ForEmptySourceFieldPath() {
    var sourceFieldPath = List.<String>of();

    assertThrows(TransformException.class, () -> new HoistFields("Brewery", "founderName", sourceFieldPath));
  }

  @Test
  void transformSchema_throwsException_ForInvalidTypeName() {
    var transform = new HoistFields("Company", "founderName", List.of("founder", "name"));

    assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema));
  }

  @Test
  void transformSchema_addsField_IfNotExists() {
    var transform = new HoistFields("Brewery", "founderName", List.of("founder", "name"));

    var transformedSchema = transform.transformSchema(originalSchema);
    var breweryType = transformedSchema.getObjectType("Brewery");

    assertThat(breweryType.getFieldDefinition("founder"), notNullValue());
    assertThat(breweryType.getFieldDefinition("founderName"), notNullValue());
  }
}
