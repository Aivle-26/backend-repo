package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AiServerProperties;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServerDocumentExtractClientTest {
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void relaysMultipartFilesToAiServer() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"llm_status":"FALLBACK","documents":[{"file_name":"project-rfp.pdf"}]}
                        """));

        AiServerDocumentExtractClient client = createClient(mockWebServer.url("/").toString(), 5, 5);

        MockMultipartFile first = new MockMultipartFile("files", "project-rfp.pdf", "application/pdf", "one".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "project-proposal.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "two".getBytes());

        AiServerJsonResponse response = client.extractDocuments(List.of(first, second));
        RecordedRequest recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.body().get("llm_status").asText()).isEqualTo("FALLBACK");
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/planning/documents/extract");
        assertThat(recordedRequest.getHeader("Content-Type")).startsWith("multipart/form-data;");

        String body = recordedRequest.getBody().readUtf8();
        assertThat(body).contains("name=\"files\"; filename=\"project-rfp.pdf\"");
        assertThat(body).contains("name=\"files\"; filename=\"project-proposal.docx\"");
        assertThat(body.indexOf("project-rfp.pdf")).isLessThan(body.indexOf("project-proposal.docx"));
        assertThat(countOccurrences(body, "name=\"files\"")).isEqualTo(2);
    }

    @Test
    void translatesAiServerPayloadTooLarge() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(413)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"detail":"File is too large."}
                        """));

        AiServerDocumentExtractClient client = createClient(mockWebServer.url("/").toString(), 5, 5);
        MockMultipartFile file = new MockMultipartFile("files", "large.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> client.extractDocuments(List.of(file)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(apiException.getCode()).isEqualTo("AI_SERVER_PAYLOAD_TOO_LARGE");
                    assertThat(apiException.getMessage()).contains("File is too large.");
                });
    }

    @Test
    void translatesAiServerValidationFailure() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"detail":"Unsupported file extension."}
                        """));

        AiServerDocumentExtractClient client = createClient(mockWebServer.url("/").toString(), 5, 5);
        MockMultipartFile file = new MockMultipartFile("files", "bad.exe", "application/octet-stream", "content".getBytes());

        assertThatThrownBy(() -> client.extractDocuments(List.of(file)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(apiException.getCode()).isEqualTo("AI_SERVER_VALIDATION_FAILED");
                    assertThat(apiException.getMessage()).contains("Unsupported file extension.");
                });
    }

    @Test
    void returnsUnavailableWhenConnectionFails() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }

        AiServerDocumentExtractClient client = createClient("http://127.0.0.1:" + unusedPort, 1, 1);
        MockMultipartFile file = new MockMultipartFile("files", "sample.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> client.extractDocuments(List.of(file)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiException.getCode()).isEqualTo("AI_SERVER_UNAVAILABLE");
                });
    }

    @Test
    void returnsTimeoutWhenAiServerDoesNotRespond() {
        mockWebServer.enqueue(new MockResponse()
                .setHeadersDelay(2, TimeUnit.SECONDS)
                .setBody("{}"));

        AiServerDocumentExtractClient client = createClient(mockWebServer.url("/").toString(), 1, 1);
        MockMultipartFile file = new MockMultipartFile("files", "sample.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> client.extractDocuments(List.of(file)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(apiException.getCode()).isEqualTo("AI_SERVER_TIMEOUT");
                });
    }

    private AiServerDocumentExtractClient createClient(String baseUrl, int connectTimeoutSeconds, int readTimeoutSeconds) {
        AiServerProperties properties = new AiServerProperties();
        properties.setBaseUrl(baseUrl);
        properties.setDocumentExtractPath("/api/v1/planning/documents/extract");
        properties.setConnectTimeoutSeconds(connectTimeoutSeconds);
        properties.setResponseTimeoutSeconds(readTimeoutSeconds);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutSeconds * 1000);
        requestFactory.setReadTimeout(readTimeoutSeconds * 1000);

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        return new AiServerDocumentExtractClient(restClient, properties, new ObjectMapper());
    }

    private long countOccurrences(String body, String token) {
        Pattern pattern = Pattern.compile(Pattern.quote(token));
        Matcher matcher = pattern.matcher(body);
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
