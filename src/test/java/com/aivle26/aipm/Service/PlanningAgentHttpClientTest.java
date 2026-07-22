package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.PlanningAgentProperties;
import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningAgentHttpClientTest {
    private static final String PROJECT_NAME = "\uD559\uC0DD \uD559\uC2B5\uD604\uD669 \uC870\uD68C \uC2DC\uC2A4\uD15C \uAD6C\uCD95";
    private static final String PROJECT_GOAL = "\uD559\uC0DD\uACFC \uAD50\uC0AC\uAC00 \uD559\uC2B5 \uC9C4\uD589 \uC0C1\uD669\uC744 \uD655\uC778\uD560 \uC218 \uC788\uB294 \uAC04\uB2E8\uD55C \uC6F9 \uC2DC\uC2A4\uD15C\uC744 \uAD6C\uCD95\uD55C\uB2E4.";
    private static final String CLIENT_ORGANIZATION = "\uD55C\uAD6D\uAD50\uC721\uC9C0\uC6D0\uC13C\uD130";
    private static final String ACCEPTANCE = "\uC751\uB2F5 \uC2DC\uAC04 3\uCD08 \uC774\uB0B4";
    private static final String FUNCTION_NAME = "\uD559\uC2B5 \uC9C4\uB3C4 \uC870\uD68C";

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
    void decodesKoreanJsonResponseUsingUtf8() {
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
                      "requirement_id": "REQ-001",
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
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "simple-learning-support-rfp.txt",
                "text/plain",
                "test".getBytes(StandardCharsets.UTF_8)
        );

        PlanningDocumentExtractResponse response = client.extractDocuments(List.of(file), true);

        assertThat(response.projectInfo().projectName()).isEqualTo(PROJECT_NAME);
        assertThat(response.projectInfo().projectGoal()).isEqualTo(PROJECT_GOAL);
        assertThat(response.projectInfo().clientOrganization()).isEqualTo(CLIENT_ORGANIZATION);
        assertThat(response.projectInfo().acceptanceConditions()).containsExactly(ACCEPTANCE);
        assertThat(response.requirementCandidates().get(0).functionName()).isEqualTo(FUNCTION_NAME);
    }

    private PlanningAgentHttpClient createClient(String baseUrl) {
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setBaseUrl(baseUrl);
        properties.setExtractPath("/api/v1/planning/documents/extract");

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
}
