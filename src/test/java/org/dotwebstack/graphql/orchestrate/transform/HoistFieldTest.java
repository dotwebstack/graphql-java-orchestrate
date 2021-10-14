package org.dotwebstack.graphql.orchestrate.transform;

import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Map;
import org.dotwebstack.graphql.orchestrate.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HoistFieldTest {

  private GraphQLSchema originalSchema;

  @BeforeEach
  void setUp() {
    originalSchema = loadSchema("dbeerpedia");
  }

  @Test
  void constructor_throwsException_ForEmptySourceFieldPath() {
    var sourceFieldPath = List.<String>of();

    assertThrows(IllegalArgumentException.class, () -> new HoistField("Brewery", "founderName", sourceFieldPath));
  }

  @Test
  void transformSchema_throwsException_ForInvalidTypeName() {
    var transform = new HoistField("Company", "founderName", List.of("founder", "name"));

    assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema));
  }

  @Test
  void transformSchema_addsField_IfNotExists() {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));
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
    var transform = new HoistField("Brewery", "founder", List.of("founder", "name"));
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
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founderName}}");
    var transformedRequest = transform.transformRequest(originalRequest);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier founder {name}}}"));
  }

  @Test
  void transformRequest_mergesSelectionSet_ifSelectionsOverlap() {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founder {identifier} founderName}}");
    var transformedRequest = transform.transformRequest(originalRequest);

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier founder {identifier name}}}"));
  }

  @Test
  void transformResult_dehoistsField_forSuccessResult() {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema);

    var originalResult = Result.newResult()
        .data(Map.of("brewery", Map.of("identifier", "foo", "founder", Map.of("name", "bar"))))
        .build();

    var transformedResult = transform.transformResult(originalResult);
  }
}
