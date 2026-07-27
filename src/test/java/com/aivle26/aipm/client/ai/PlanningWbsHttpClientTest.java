package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningWbsHttpClientTest {
    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void sendsRequirementsAndDecodesWbsOnlyResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "agent_execution_id": "wbs-execution-1",
                          "agent_version": "planning-wbs-v1",
                          "tasks": [
                            {
                              "external_task_id": "TASK-001",
                              "parent_external_task_id": null,
                              "task_code": "1",
                              "task_name": "요구사항 분석",
                              "description": "확정 요구사항을 분석한다.",
                              "phase": "ANALYSIS",
                              "required_skills": ["REQUIREMENTS_ANALYSIS"],
                              "difficulty": "MEDIUM",
                              "estimated_hours": 8,
                              "order_index": 0,
                              "requirement_ids": [10]
                            }
                          ]
                        }
                        """));

        PlanningWbsHttpClient client = createClient();
        var response = client.generateWbs(new PlanningWbsGenerationRequest(List.of(
                new PlanningWbsGenerationRequest.RequirementData(
                        10L,
                        "FUNCTIONAL",
                        "로그인",
                        "사용자는 로그인할 수 있어야 한다.",
                        null,
                        null,
                        null,
                        null,
                        "HIGH"
                )
        )));

        assertThat(response.agentExecutionId()).isEqualTo("wbs-execution-1");
        assertThat(response.tasks()).singleElement().satisfies(task -> {
            assertThat(task.taskName()).isEqualTo("요구사항 분석");
            assertThat(task.requirementIds()).containsExactly(10L);
        });

        JsonNode sentBody = objectMapper.readTree(mockWebServer.takeRequest().getBody().readUtf8());
        assertThat(sentBody.fieldNames()).toIterable().containsExactly("requirements");
        assertThat(sentBody.at("/requirements/0/requirement_id").asLong()).isEqualTo(10L);
        assertThat(sentBody.at("/requirements/0/title").asText()).isEqualTo("로그인");
    }

    private PlanningWbsHttpClient createClient() {
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());
        properties.setWbsPath("/api/v1/planning/wbs/generate");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new PlanningWbsHttpClient(restClient, properties, objectMapper);
    }
}
