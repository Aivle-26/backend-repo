package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Dto.project.PlanningRequirementReadjustResponse;
import com.aivle26.aipm.Exception.ApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningAgentHttpClientTest {
    private static final String PROJECT_NAME = "\uD559\uC0DD \uD559\uC2B5\uD604\uD669 \uC870\uD68C \uC2DC\uC2A4\uD15C \uAD6C\uCD95";
    private static final String PROJECT_GOAL = "\uD559\uC0DD\uACFC \uAD50\uC0AC\uAC00 \uD559\uC2B5 \uC9C4\uD589 \uC0C1\uD669\uC744 \uD655\uC778\uD560 \uC218 \uC788\uB294 \uAC04\uB2E8\uD55C \uC6F9 \uC2DC\uC2A4\uD15C\uC744 \uAD6C\uCD95\uD55C\uB2E4.";
    private static final String CLIENT_ORGANIZATION = "\uD55C\uAD6D\uAD50\uC721\uC9C0\uC6D0\uC13C\uD130";
    private static final String ACCEPTANCE = "\uC751\uB2F5 \uC2DC\uAC04 3\uCD08 \uC774\uB0B4";
    private static final String FUNCTION_NAME = "\uD559\uC2B5 \uC9C4\uB3C4 \uC870\uD68C";

    private MockWebServer mockWebServer;
    private Path tempDirectory;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        tempDirectory = Files.createTempDirectory("planning-agent-client-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
        if (tempDirectory != null) {
            try (var paths = Files.walk(tempDirectory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void decodesKoreanJsonResponseUsingUtf8() throws Exception {
        String responseBody = """
                {
                  "project_info": {
                    "project_name": "%s",
                    "project_goal": "%s",
                    "client_organization": "%s",
                    "period_start": "2026-09-01",
                    "period_end": "2026-11-30",
                    "key_features": ["\uB85C\uADF8\uC778", "%s"],
                    "required_artifacts": [
                      {
                        "artifact_type": "REQUIREMENTS_DEFINITION",
                        "artifact_name": "\uC694\uAD6C\uC0AC\uD56D\uC815\uC758\uC11C",
                        "required_version": "v1.0"
                      }
                    ],
                    "acceptance_conditions": ["%s"],
                    "budget_contract_conditions": ["\uC608\uC0B0 \uBC94\uC704 \uB0B4 \uAC1C\uBC1C"],
                    "security_privacy_conditions": ["\uAC1C\uC778\uC815\uBCF4 \uC811\uADFC \uD1B5\uC81C"]
                  },
                  "requirement_candidates": [
                    {
                      "requirement_id": 1,
                      "function_name": "%s",
                      "requirement_text": "\uD559\uC0DD\uC740 \uBCF8\uC778\uC758 \uACFC\uBAA9\uBCC4 \uD559\uC2B5 \uC9C4\uD589\uB960\uC744 \uC870\uD68C\uD560 \uC218 \uC788\uC5B4\uC57C \uD55C\uB2E4.",
                      "category": "FUNCTIONAL",
                      "priority": "HIGH",
                      "acceptance_criteria": "\uD559\uC0DD \uACC4\uC815\uC73C\uB85C \uB85C\uADF8\uC778 \uC2DC \uBCF8\uC778\uC758 \uD559\uC2B5 \uC815\uBCF4\uB9CC \uD45C\uC2DC\uB418\uC5B4\uC57C \uD55C\uB2E4.",
                      "due_date": "2026-10-01",
                      "deliverable_name": "\uC694\uAD6C\uC0AC\uD56D\uC815\uC758\uC11C",
                      "security_condition": "\uBCF8\uC778 \uB370\uC774\uD130\uB9CC \uC870\uD68C \uAC00\uB2A5",
                      "source_document": "simple-learning-support-rfp.txt",
                      "source_excerpt": "\uD559\uC0DD\uC740 \uB85C\uADF8\uC778 \uD6C4 \uBCF8\uC778\uC758 \uD559\uC2B5 \uC9C4\uB3C4\uB97C \uC870\uD68C\uD560 \uC218 \uC788\uC5B4\uC57C \uD55C\uB2E4."
                    }
                  ],
                  "documents": [
                    {
                      "file_name": "simple-learning-support-rfp.txt",
                      "file_type": "TXT",
                      "character_count": 1000,
                      "processing_mode": "TEXT"
                    }
                  ],
                  "llm_status": "SUCCEEDED"
                }
                """.formatted(PROJECT_NAME, PROJECT_GOAL, CLIENT_ORGANIZATION, FUNCTION_NAME, ACCEPTANCE, FUNCTION_NAME);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        PlanningAgentHttpClient client = createClient(mockWebServer.url("/").toString());
        Path storedFilePath = tempDirectory.resolve("simple-learning-support-rfp.txt");
        Files.writeString(storedFilePath, "test", StandardCharsets.UTF_8);
        StoredDocumentFile file = new StoredDocumentFile(
                "simple-learning-support-rfp.txt",
                "text/plain",
                Files.size(storedFilePath),
                Files.readAllBytes(storedFilePath)
        );

        PlanningDocumentExtractResponse response = client.extractDocuments(List.of(file), true);

        assertThat(response.projectInfo().projectName()).isEqualTo(PROJECT_NAME);
        assertThat(response.projectInfo().projectGoal()).isEqualTo(PROJECT_GOAL);
        assertThat(response.projectInfo().clientOrganization()).isEqualTo(CLIENT_ORGANIZATION);
        assertThat(response.projectInfo().acceptanceConditions()).containsExactly(ACCEPTANCE);
        assertThat(response.requirementCandidates().get(0).requirementId()).isEqualTo(1L);
        assertThat(response.requirementCandidates().get(0).functionName()).isEqualTo(FUNCTION_NAME);
    }

    @Test
    void sendsStableDocumentManifestWhenDatabaseIdIsAvailable() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_info": {},
                          "requirement_candidates": [],
                          "documents": [],
                          "llm_status": "SUCCEEDED"
                        }
                        """));
        PlanningAgentHttpClient client =
                createClient(mockWebServer.url("/").toString());
        StoredDocumentFile file = new StoredDocumentFile(
                "rfp.txt",
                "text/plain",
                4,
                "test".getBytes(StandardCharsets.UTF_8),
                12L
        );

        client.extractDocuments(List.of(file), true);

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertThat(body).contains("name=\"document_manifest\"");
        assertThat(body)
                .contains("\"document_id\":12")
                .contains("\"file_name\":\"rfp.txt\"");
    }

    @Test
    void sendsReadjustmentToDedicatedEndpoint() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "change_candidates": [],
                          "documents": [],
                          "llm_status": "SUCCEEDED"
                        }
                        """));
        PlanningAgentHttpClient client =
                createClient(mockWebServer.url("/").toString());
        StoredDocumentFile file = new StoredDocumentFile(
                "change.txt",
                "text/plain",
                4,
                "test".getBytes(StandardCharsets.UTF_8),
                15L
        );
        var existing = new PlanningRequirementReadjustResponse.ExistingRequirement(
                10L,
                "Login",
                "Users must log in.",
                "FUNCTIONAL",
                "HIGH",
                null,
                null,
                null,
                null,
                "base.pdf",
                null,
                List.of()
        );

        client.readjustRequirements(List.of(file), List.of(existing));

        var request = mockWebServer.takeRequest();
        assertThat(request.getPath())
                .isEqualTo("/api/v1/planning/documents/readjust");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"existing_requirements\"");
        assertThat(body).contains("\"requirement_id\":10");
        assertThat(body).contains("\"document_id\":15");
    }

    @Test
    void mapsAgentValidationFailureToUnprocessableEntity() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"invalid multipart\"}"));

        assertClientError(
                createClient(mockWebServer.url("/").toString()),
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PLANNING_AGENT_INVALID_REQUEST"
        );
    }

    @Test
    void mapsInvalidAgentJsonToBadGateway() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{not-json"));

        assertClientError(
                createClient(mockWebServer.url("/").toString()),
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_AGENT_RESPONSE"
        );
    }

    @Test
    void mapsAgentServerFailureToBadGateway() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"internal\"}"));

        assertClientError(
                createClient(mockWebServer.url("/").toString()),
                HttpStatus.BAD_GATEWAY,
                "PLANNING_AGENT_SERVER_ERROR"
        );
    }

    @Test
    void mapsConnectionRefusalToServiceUnavailable() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }

        assertClientError(
                createClient("http://127.0.0.1:" + unusedPort),
                HttpStatus.SERVICE_UNAVAILABLE,
                "PLANNING_AGENT_UNAVAILABLE"
        );
    }

    @Test
    void mapsReadTimeoutToGatewayTimeout() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{}")
                .setBodyDelay(2, TimeUnit.SECONDS));

        assertClientError(
                createClient(
                        mockWebServer.url("/").toString(),
                        Duration.ofMillis(200)
                ),
                HttpStatus.GATEWAY_TIMEOUT,
                "PLANNING_AGENT_TIMEOUT"
        );
    }

    private PlanningAgentHttpClient createClient(String baseUrl) {
        return createClient(baseUrl, Duration.ofSeconds(5));
    }

    private PlanningAgentHttpClient createClient(String baseUrl, Duration readTimeout) {
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setBaseUrl(baseUrl);
        properties.setExtractPath("/api/v1/planning/documents/extract");
        properties.setReadjustPath("/api/v1/planning/documents/readjust");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(readTimeout);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new PlanningAgentHttpClient(restClient, properties, objectMapper);
    }

    private void assertClientError(
            PlanningAgentHttpClient client,
            HttpStatus expectedStatus,
            String expectedCode
    ) {
        StoredDocumentFile file = new StoredDocumentFile(
                "source.txt",
                "text/plain",
                6,
                "source".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> client.extractDocuments(List.of(file), true))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(expectedStatus);
                            assertThat(exception.getCode()).isEqualTo(expectedCode);
                        }
                );
    }
}
