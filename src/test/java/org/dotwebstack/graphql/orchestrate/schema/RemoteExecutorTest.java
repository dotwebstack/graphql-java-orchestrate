package org.dotwebstack.graphql.orchestrate.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionInput;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.dotwebstack.graphql.orchestrate.exception.GraphqlJavaOrchestrateException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

class RemoteExecutorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static MockWebServer mockWebServer;

  private static WebClient webClient;

  @BeforeAll
  static void beforeAll() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector())
        .codecs(configurer -> configurer.defaultCodecs()
            .jackson2JsonEncoder(new Jackson2JsonEncoder(OBJECT_MAPPER, MediaType.APPLICATION_JSON)))
        .build();
  }

  @AfterAll
  static void afterAll() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void execute_performsHttpCall_OnGivenEndpoint() throws Exception {
    var executor = getRemoteExecutor();

    var query = "{brewery(identifier:\"foo\") {identifier name}}";

    var input = ExecutionInput.newExecutionInput()
        .query(query)
        .build();

    var resultData = Map.of("brewery", Map.of("identifier", "foo", "name", "bar"));

    mockWebServer.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .setBody(OBJECT_MAPPER.writeValueAsString(Map.of("data", resultData))));

    var result = executor.execute(input)
        .get();

    assertThat(result, notNullValue());
    assertThat(result.getData(), equalTo(resultData));

    var request = mockWebServer.takeRequest();
    var requestBody = getRequestBody(request);

    assertThat(request.getHeader(HttpHeaders.ACCEPT), equalTo(MediaType.APPLICATION_JSON_VALUE));
    assertThat(requestBody.get("query"), equalTo(query));
    assertThat(requestBody.get("variables"), equalTo(Map.of()));
  }

  @Test
  void execute_passesThroughVars_WhenVarsPresent() throws Exception {
    var executor = getRemoteExecutor();

    var query = "query Query($identifier: String) {brewery($identifier) {identifier name}}";
    Map<String, Object> variables = Map.of("identifier", "foo");

    var input = ExecutionInput.newExecutionInput()
        .query(query)
        .variables(variables)
        .build();

    var resultData = Map.of("brewery", Map.of("identifier", "foo", "name", "bar"));

    mockWebServer.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .setBody(OBJECT_MAPPER.writeValueAsString(Map.of("data", resultData))));

    var result = executor.execute(input)
        .get();

    assertThat(result, notNullValue());
    assertThat(result.getData(), equalTo(resultData));

    var request = mockWebServer.takeRequest();
    var requestBody = getRequestBody(request);

    assertThat(request.getHeader(HttpHeaders.ACCEPT), equalTo(MediaType.APPLICATION_JSON_VALUE));
    assertThat(requestBody.get("query"), equalTo(query));
    assertThat(requestBody.get("variables"), equalTo(variables));
  }

  @Test
  void execute_passesThroughExceptionDetails_WhenExceptionDetailsPresent() throws Exception {
    var executor = getRemoteExecutor();

    var query = "query Query($identifier: String) {brewery($identifier) {identifier name}}";
    Map<String, Object> variables = Map.of("identifier", "foo");

    var input = ExecutionInput.newExecutionInput()
        .query(query)
        .variables(variables)
        .build();

    mockWebServer.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .setBody(OBJECT_MAPPER.writeValueAsString(Map.of("status", "400", "detail", "Bad request"))));

    var result = executor.execute(input)
        .get();

    assertThat(result, notNullValue());
    assertThat(result.getErrors()
        .size(), is(1));
    var error = result.getErrors()
        .get(0);

    assertThat(error.getClass(), is(ExceptionWhileDataFetching.class));
    var exception = ((ExceptionWhileDataFetching) error).getException();

    assertThat(exception.getClass(), is(GraphqlJavaOrchestrateException.class));
    var graphqlException = (GraphqlJavaOrchestrateException) exception;

    assertThat(graphqlException.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    assertThat(graphqlException.getMessage(), is("Bad request"));

    mockWebServer.takeRequest();
  }

  @Test
  void execute_passesThroughInternalServerError_WhenUnknownBodyDetails() throws Exception {
    var executor = getRemoteExecutor();

    var query = "query Query($identifier: String) {brewery($identifier) {identifier name}}";
    Map<String, Object> variables = Map.of("identifier", "foo");

    var input = ExecutionInput.newExecutionInput()
        .query(query)
        .variables(variables)
        .build();

    mockWebServer.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .setBody(OBJECT_MAPPER.writeValueAsString(Map.of("unknown", "unknown"))));

    var result = executor.execute(input)
        .get();

    assertThat(result, notNullValue());
    assertThat(result.getErrors()
        .size(), is(1));
    var error = result.getErrors()
        .get(0);

    assertThat(error.getClass(), is(ExceptionWhileDataFetching.class));
    var exception = ((ExceptionWhileDataFetching) error).getException();

    assertThat(exception.getClass(), is(GraphqlJavaOrchestrateException.class));
    var graphqlException = (GraphqlJavaOrchestrateException) exception;

    assertThat(graphqlException.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
    assertThat(graphqlException.getMessage(), is("Something went wrong while orchestrating the request."));

    mockWebServer.takeRequest();
  }

  private static RemoteExecutor getRemoteExecutor() {
    return RemoteExecutor.newExecutor()
        .endpoint(URI.create(String.format("http://%s:%d", mockWebServer.getHostName(), mockWebServer.getPort())))
        .webClient(webClient)
        .build();
  }

  private Map<String, Object> getRequestBody(RecordedRequest request) throws JsonProcessingException {
    var typeRef = new TypeReference<Map<String, Object>>() {};
    var bodyText = request.getBody()
        .readUtf8();

    return new ObjectMapper().readValue(bodyText, typeRef);
  }
}
