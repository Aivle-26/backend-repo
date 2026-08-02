package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.DeliverableRagRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectAssistantHttpClientTest {

    private MockWebServer server;
    private ProjectAssistantHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        PlanningAgentProperties properties = new PlanningAgentProperties();
        client = new ProjectAssistantHttpClient(
                RestClient.builder().baseUrl(server.url("/").toString()).build(), properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void sendsRagContractAndReturnsSources() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {
                  "project_id": 1,
                  "answer": "로그인 요구사항입니다.",
                  "sources": [{
                    "deliverable_id": "requirement",
                    "document_id": "requirement-10",
                    "document_name": "요구사항: 로그인",
                    "page": null,
                    "excerpt": "SSO 로그인을 지원합니다.",
                    "requirement_id": 10,
                    "wbs_id": null,
                    "review_status": "APPROVED"
                  }],
                  "generated_at": "2026-08-02T15:00:00+09:00",
                  "llm_status": "SUCCEEDED"
                }
                """));

        var request = new DeliverableRagRequest(1L, "로그인 조건은?", List.of(
                new DeliverableRagRequest.Document("requirement", "requirement-10", "요구사항: 로그인",
                        "SSO 로그인을 지원합니다.", null, 10L, null, "APPROVED")), true);
        var response = client.query(request);
        var recorded = server.takeRequest();

        assertThat(recorded.getPath()).isEqualTo("/api/v1/reports/deliverables/rag/query");
        String body = recorded.getBody().readUtf8();
        assertThat(body).contains("\"project_id\":1");
        assertThat(body).contains("\"deliverable_documents\"");
        assertThat(body).contains("\"enable_llm\":true");
        assertThat(response.llmStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.sources()).hasSize(1);
    }
}
