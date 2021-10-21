package org.dotwebstack.graphql.orchestrate.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    var executor = RemoteExecutor.newExecutor()
        .endpoint(URI.create(String.format("http://%s:%d", mockWebServer.getHostName(), mockWebServer.getPort())))
        .webClient(webClient)
        .build();

    var input = ExecutionInput.newExecutionInput()
        .query("{brewery(identifier:\"foo\") {identifier name}}")
        .build();

    var resultData = Map.of("brewery", Map.of("identifier", "foo", "name", "bar"));

    mockWebServer.enqueue(new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .setBody(OBJECT_MAPPER.writeValueAsString(Map.of("data", resultData))));

    var result = executor.execute(input)
        .get();

    assertThat(result, notNullValue());
    assertThat(result.getData(), equalTo(resultData));
  }
}
