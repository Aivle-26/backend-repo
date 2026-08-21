package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningScheduleRecommendRequest;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class PlanningScheduleHttpClientTest {
    private MockWebServer server;
    private PlanningScheduleHttpClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = JsonMapper.builder().findAndAddModules().build();

        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setSchedulePath("/api/v1/planning/schedules/recommend");
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new PlanningScheduleHttpClient(
                restClient,
                properties,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void recommendSchedulesUsesConfiguredPathAndSnakeCaseContract()
            throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_id": 1,
                          "wbs_schedules": [
                            {
                              "wbs_id": 101,
                              "expected": {
                                "start_date": "2026-08-03",
                                "end_date": "2026-08-05"
                              },
                              "recommended": {
                                "start_date": "2026-08-03",
                                "end_date": "2026-08-06"
                              },
                              "conservative": {
                                "start_date": "2026-08-03",
                                "end_date": "2026-08-07"
                              },
                              "predecessor_wbs_ids": [100],
                              "milestone": true,
                              "buffer_days": 2
                            }
                          ],
                          "warnings": ["target end date exceeded"],
                          "llm_status": "SUCCEEDED"
                        }
                        """));

        var response = client.recommendSchedules(new PlanningScheduleRecommendRequest(
                1L,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 12, 31),
                List.of(new PlanningScheduleRecommendRequest.ScheduleWbsItem(
                        101L,
                        "1.1",
                        100L,
                        PlanningScheduleRecommendRequest.ItemType.TASK,
                        "Requirement review",
                        "Review requirements"
                ))
        ));

        var recordedRequest = server.takeRequest();
        var requestJson = objectMapper.readTree(recordedRequest.getBody().readUtf8());

        assertThat(recordedRequest.getPath())
                .isEqualTo("/api/v1/planning/schedules/recommend");
        assertThat(requestJson.path("project_id").asLong()).isEqualTo(1L);
        assertThat(requestJson.path("wbs_items").get(0).path("wbs_id").asLong())
                .isEqualTo(101L);
        assertThat(requestJson.path("wbs_items").get(0).path("parent_wbs_id").asLong())
                .isEqualTo(100L);
        assertThat(requestJson.path("wbs_items").get(0).path("item_type").asText())
                .isEqualTo("TASK");
        assertThat(response.wbsSchedules().getFirst().recommended().endDate())
                .isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(response.wbsSchedules().getFirst().predecessorWbsIds())
                .containsExactly(100L);
        assertThat(response.wbsSchedules().getFirst().milestone()).isTrue();
        assertThat(response.wbsSchedules().getFirst().bufferDays()).isEqualTo(2);
        assertThat(response.llmStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.warnings()).containsExactly("target end date exceeded");
    }

    @Test
    void logsSanitizedAiValidationErrorWithRequestSummary(CapturedOutput output) {
        server.enqueue(new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "detail": [
                            {
                              "type": "value_error",
                              "loc": ["body"],
                              "msg": "Value error, invalid WBS hierarchy",
                              "input": {"wbs_name": "sensitive-project-data"}
                            }
                          ]
                        }
                        """));

        assertThatThrownBy(() -> client.recommendSchedules(request()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PLANNING_SCHEDULE_CLIENT_ERROR")
                );

        assertThat(output.getOut())
                .contains("status=422")
                .contains("path=/api/v1/planning/schedules/recommend")
                .contains("projectId=1")
                .contains("wbsItemCount=1")
                .contains("type=value_error")
                .contains("loc=/body")
                .contains("msg=Value error, invalid WBS hierarchy")
                .doesNotContain("sensitive-project-data");
    }

    @Test
    void logsAiServerFailureDetailWithRequestSummary(CapturedOutput output) {
        server.enqueue(new MockResponse()
                .setResponseCode(502)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"detail":"schedule generation failed"}
                        """));

        assertThatThrownBy(() -> client.recommendSchedules(request()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PLANNING_SCHEDULE_SERVER_ERROR")
                );

        assertThat(output.getOut())
                .contains("status=502")
                .contains("path=/api/v1/planning/schedules/recommend")
                .contains("projectId=1")
                .contains("wbsItemCount=1")
                .contains("detail=schedule generation failed");
    }

    private PlanningScheduleRecommendRequest request() {
        return new PlanningScheduleRecommendRequest(
                1L,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 12, 31),
                List.of(new PlanningScheduleRecommendRequest.ScheduleWbsItem(
                        101L,
                        "1.1",
                        100L,
                        PlanningScheduleRecommendRequest.ItemType.TASK,
                        "Requirement review",
                        "Review requirements"
                ))
        );
    }
}
