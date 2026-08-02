package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.CostEstimateRequest;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningCostHttpClientTest {

    private MockWebServer server;
    private PlanningCostHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setCostPath("/api/v1/planning/costs/estimate");
        client = new PlanningCostHttpClient(
                RestClient.builder().baseUrl(server.url("/").toString()).build(),
                properties
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void usesCostPathAndSnakeCaseContract() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_id": 101,
                          "currency": "KRW",
                          "total_estimated_mm": 0.3,
                          "cost_summary": {
                            "labor_cost": 2400000,
                            "server_cost": 1200000,
                            "license_cost": 0,
                            "ai_api_cost": 900000,
                            "base_cost": 4500000
                          },
                          "estimate": {
                            "contingency_rate": 10,
                            "contingency_amount": 450000,
                            "supply_amount": 4950000,
                            "vat": 495000,
                            "total_amount": 5445000
                          },
                          "unpriced_items": ["External data"],
                          "warning": "Actual costs may vary.",
                          "llm_status": "FALLBACK"
                        }
                        """));

        var response = client.estimate(request());
        var recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();

        assertThat(recorded.getPath()).isEqualTo("/api/v1/planning/costs/estimate");
        assertThat(body).contains("\"average_monthly_unit_price\":8000000");
        assertThat(body).contains("\"estimated_mm\":0.3");
        assertThat(body).contains("\"include_vat\":true");
        assertThat(response.llmStatus()).isEqualTo("FALLBACK");
        assertThat(response.unpricedItems()).containsExactly("External data");
    }

    private PlanningCostEstimateRequest request() {
        return new PlanningCostEstimateRequest(
                101L,
                "AIPM",
                List.of(new PlanningCostEstimateRequest.WbsEffort(
                        3L,
                        "API implementation",
                        "Integrate the AI API",
                        0.3
                )),
                8_000_000L,
                12,
                CostEstimateRequest.ServiceScale.MEDIUM,
                true,
                0,
                true
        );
    }
}
