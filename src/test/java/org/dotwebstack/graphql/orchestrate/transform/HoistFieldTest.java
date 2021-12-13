package org.dotwebstack.graphql.orchestrate.transform;

import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.loadSchema;
import static org.dotwebstack.graphql.orchestrate.test.TestUtils.parseQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import graphql.language.AstPrinter;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.dotwebstack.graphql.orchestrate.Request;
import org.dotwebstack.graphql.orchestrate.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoistFieldTest {

  @Mock
  private TransformContext context;

  @Mock
  private Function<Request, CompletableFuture<Result>> nextMock;

  @Captor
  private ArgumentCaptor<Request> requestCaptor;

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
  void constructor_throwsException_ForSourceFieldPathWithSingleField() {
    var sourceFieldPath = List.of("name");

    assertThrows(IllegalArgumentException.class, () -> new HoistField("Brewery", "breweryName", sourceFieldPath));
  }

  @Test
  void transformSchema_throwsException_ForInvalidTypeName() {
    var transform = new HoistField("Company", "founderName", List.of("founder", "name"));

    assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema, context));
  }

  @Test
  void transformSchema_throwsException_ForInvalidFieldName() {
    var transform = new HoistField("Brewery", "founderAge", List.of("founder", "age"));

    assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema, context));
  }

  @Test
  void transformSchema_addsField_IfNotExists() {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));
    var transformedSchema = transform.transformSchema(originalSchema, context);

    var targetField = transformedSchema.getObjectType("Brewery")
        .getFieldDefinition("founderName");

    assertThat(targetField, notNullValue());
    assertThat(targetField.getType(), equalTo(originalSchema.getObjectType("Person")
        .getFieldDefinition("name")
        .getType()));
  }

  @Test
  void transformSchema_addsListField_IfPathContainsList() {
    var transform = new HoistField("Brewery", "ambassadorNames", List.of("ambassadors", "name"));
    var transformedSchema = transform.transformSchema(originalSchema, context);

    var targetField = transformedSchema.getObjectType("Brewery")
        .getFieldDefinition("ambassadorNames");

    assertThat(targetField, notNullValue());
    assertThat(targetField.getType(), instanceOf(GraphQLNonNull.class));

    var listType = unwrapNonNull(targetField.getType());

    assertThat(listType, instanceOf(GraphQLList.class));
    assertThat(((GraphQLList) listType).getWrappedType(), equalTo(originalSchema.getObjectType("Person")
        .getFieldDefinition("name")
        .getType()));
  }

  @Test
  void transformSchema_throwsException_IfPathContainsMultipleNonLeafLists() {
    var transform = new HoistField("Brewery", "ambassadorHobbies", List.of("ambassadors", "hobbies", "id"));

    assertThrows(TransformException.class, () -> transform.transformSchema(originalSchema, context));
  }

  @Test
  void transformSchema_IfPathContainsOneNonLeafList() {
    var transform = new HoistField("Brewery", "ambassadorHobbies", List.of("ambassadors", "hobbies"));
    var transformedSchema = transform.transformSchema(originalSchema, context);

    var targetField = transformedSchema.getObjectType("Brewery")
        .getFieldDefinition("ambassadorHobbies");

    assertThat(targetField, notNullValue());
    assertThat(targetField.getType(), instanceOf(GraphQLNonNull.class));

    var listType = unwrapOne(unwrapNonNull(targetField.getType()));

    assertThat(listType, equalTo(originalSchema.getObjectType("Person")
        .getFieldDefinition("hobbies")
        .getType()));
  }

  @Test
  void transformSchema_replacesField_IfExists() {
    var transform = new HoistField("Brewery", "founder", List.of("founder", "name"));
    var transformedSchema = transform.transformSchema(originalSchema, context);

    var targetField = transformedSchema.getObjectType("Brewery")
        .getFieldDefinition("founder");

    assertThat(targetField, notNullValue());
    assertThat(targetField.getType(), equalTo(originalSchema.getObjectType("Person")
        .getFieldDefinition("name")
        .getType()));
  }

  @Test
  void transform_returnsNull_ifNextReturnsNull() throws Exception {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema, context);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founderName}}");

    Map<String, Object> data = new HashMap<>();
    data.put("brewery", null);

    var proxyResult = Result.newResult()
        .data(data)
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    assertThat(result.getData(), equalTo(data));
  }

  @Test
  @SuppressWarnings("unchecked")
  void transform_expandsSelectionSet_ifFieldRequested() throws Exception {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema, context);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founderName}}");

    var proxyResult = Result.newResult()
        .data(Map.of("brewery", Map.of("identifier", "foo", "founder", Map.of("name", "bar"))))
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    Map<String, Object> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    assertThat(((Map<String, Object>) resultData.get("brewery")).get("founderName"), equalTo("bar"));

    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier founder {name}}}"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void transform_replacesSelectionSet_ifFieldOverlaps() throws Exception {
    var transform = new HoistField("Brewery", "founder", List.of("founder", "name"));

    transform.transformSchema(originalSchema, context);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founder}}");

    var proxyResult = Result.newResult()
        .data(Map.of("brewery", Map.of("identifier", "foo", "founder", Map.of("name", "bar"))))
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    Map<String, Object> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    assertThat(((Map<String, Object>) resultData.get("brewery")).get("founder"), equalTo("bar"));

    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier founder {name}}}"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void transform_mergesSelectionSet_ifSelectionsOverlap() throws Exception {
    var transform = new HoistField("Brewery", "founderName", List.of("founder", "name"));

    transform.transformSchema(originalSchema, context);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founder {identifier} founderName}}");

    var proxyResult = Result.newResult()
        .data(Map.of("brewery", Map.of("identifier", "foo", "founder", Map.of("identifier", "baz", "name", "bar"))))
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    Map<String, Object> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    assertThat(((Map<String, Object>) resultData.get("brewery")).get("founderName"), equalTo("bar"));

    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier founder {identifier name}}}"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void transform_returnsListField_ifLeafSourceFieldIsList() throws Exception {
    var transform = new HoistField("Brewery", "ambassadorNames", List.of("ambassadors", "name"));

    transform.transformSchema(originalSchema, context);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier ambassadorNames}}");

    var proxyResult = Result.newResult()
        .data(Map.of("brewery", Map.of("identifier", "foo", "ambassadors", List.of(Map.of("name", "bar")))))
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    Map<String, Object> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    assertThat(((Map<String, Object>) resultData.get("brewery")).get("ambassadorNames"), equalTo(List.of("bar")));

    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier ambassadors {name}}}"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void transform_returnsListField_ifOtherSourceFieldIsList() throws Exception {
    var transform = new HoistField("Brewery", "ambassadorNames", List.of("ambassadors", "name"));

    transform.transformSchema(originalSchema, context);

    var originalRequest =
        parseQuery("{brewery(identifier:\"foo\") {identifier collaborators {identifier ambassadorNames}}}");

    var proxyResult = Result.newResult()
        .data(Map.of("brewery",
            Map.of("identifier", "foo", "collaborators",
                List.of(Map.of("identifier", "baz", "ambassadors", List.of(Map.of("name", "bar")))))))
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    Map<String, Object> resultData = result.getData();
    assertThat(resultData.size(), is(1));

    Map<String, Object> brewery = (Map<String, Object>) resultData.get("brewery");
    assertThat(brewery.containsKey("ambassadorNames"), is(false));

    Map<String, Object> collaborator = ((List<Map<String, Object>>) brewery.get("collaborators")).get(0);
    assertThat(collaborator.get("ambassadorNames"), equalTo(List.of("bar")));

    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier collaborators {identifier ambassadors {name}}}}"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void transform_returnsObjectField_ifLeafSourceFieldIsObject() throws Exception {
    var transform = new HoistField("Brewery", "founderAddress", List.of("founder", "address"));

    transform.transformSchema(originalSchema, context);

    var originalRequest = parseQuery("{brewery(identifier:\"foo\") {identifier founderAddress{street}}}");

    var proxyResult = Result.newResult()
        .data(Map.of("brewery", Map.of("identifier", "foo", "founder", Map.of("address", Map.of("street", "bar")))))
        .build();

    when(nextMock.apply(requestCaptor.capture())).thenReturn(CompletableFuture.completedFuture(proxyResult));

    var result = transform.transform(originalRequest, nextMock)
        .get();

    Map<String, Object> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    assertThat(((Map<String, Object>) resultData.get("brewery")).get("founderAddress"),
        equalTo(Map.of("street", "bar")));

    var transformedRequest = requestCaptor.getValue();

    assertThat(AstPrinter.printAstCompact(transformedRequest.getSelectionSet()),
        equalTo("{brewery(identifier:\"foo\") {identifier founder {address {street}}}}"));
  }
}
