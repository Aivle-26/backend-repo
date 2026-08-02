package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningResourceHttpClientTest {

    private MockWebServer server;
    private PlanningResourceHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setResourcePath("/api/v1/planning/resources/recommend");
        client = new PlanningResourceHttpClient(
                RestClient.builder().baseUrl(server.url("/").toString()).build(),
                properties
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void usesResourcePathAndSnakeCaseContract() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_id": 101,
                          "required_staffing": [],
                          "assignments": [],
                          "total_estimated_person_days": 6.0,
                          "total_estimated_hours": 48.0,
                          "total_estimated_mm": 0.3,
                          "unassigned_wbs_ids": [3],
                          "warnings": ["No qualified assignee"],
                          "llm_status": "FALLBACK"
                        }
                        """));

        var response = client.recommendAssignments(request());
        var recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();

        assertThat(recorded.getPath())
                .isEqualTo("/api/v1/planning/resources/recommend");
        assertThat(body).contains("\"project_id\":101");
        assertThat(body).contains("\"project_member_id\":1");
        assertThat(body).contains("\"available_hours_per_week\":32.0");
        assertThat(response.llmStatus()).isEqualTo("FALLBACK");
        assertThat(response.unassignedWbsIds()).containsExactly(3L);
    }

    private PlanningResourceRecommendRequest request() {
        return new PlanningResourceRecommendRequest(
                101L,
                List.of(new PlanningResourceRecommendRequest.WbsTask(
                        3L,
                        "API implementation",
                        "Integrate the AI API",
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 17)
                )),
                List.of(new PlanningResourceRecommendRequest.ProjectMember(
                        1L,
                        List.of("BACKEND"),
                        List.of(new PlanningResourceRecommendRequest.Skill(
                                "JAVA", 4, 36
                        )),
                        List.of(new PlanningResourceRecommendRequest.Allocation(
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 17),
                                32.0,
                                "ACTIVE"
                        ))
                ))
        );
    }
}
