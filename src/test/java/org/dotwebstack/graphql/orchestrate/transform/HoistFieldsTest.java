package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.language.AstPrinter;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLSchema;
import java.util.List;
import org.dotwebstack.graphql.orchestrate.Request;
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

    assertThrows(IllegalArgumentException.class, () -> new HoistFields("Brewery", "founderName", sourceFieldPath));
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

    var targetField = transformedSchema.getObjectType("Brewery")
        .getFieldDefinition("founderName");

    assertThat(targetField, notNullValue());
    assertThat(targetField.getType(), equalTo(originalSchema.getObjectType("Person")
        .getFieldDefinition("name")
        .getType()));
  }

  @Test
  void transformSchema_replacesField_IfExists() {
    var transform = new HoistFields("Brewery", "founder", List.of("founder", "name"));
    var transformedSchema = transform.transformSchema(originalSchema);

    var targetField = transformedSchema.getObjectType("Brewery")
        .getFieldDefinition("founder");

    assertThat(targetField, notNullValue());
    assertThat(targetField.getType(), equalTo(originalSchema.getObjectType("Person")
        .getFieldDefinition("name")
        .getType()));
  }

  @Test
  void transformRequest_expandsSelectionSet_ifFieldRequested() {
    var transform = new HoistFields("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founderName}}");
    var transformedRequest = transform.transformRequest(originalRequest);

    var foo = AstPrinter.printAstCompact(transformedRequest.getSelectionSet());

    return;
  }
}
