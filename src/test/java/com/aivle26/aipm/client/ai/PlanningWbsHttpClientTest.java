package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class PlanningWbsHttpClientTest {
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
    void sendsNativeWbsContractAndDecodesHierarchicalResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_name": "프로젝트",
                          "methodology": ["요구사항 분석"],
                          "wbs_items": [
                            {
                              "wbs_id": 1,
                              "wbs_code": "1",
                              "parent_wbs_id": null,
                              "level": 1,
                              "sort_order": 1,
                              "item_type": "PHASE",
                              "wbs_name": "요구사항 분석",
                              "description": "요구사항을 분석한다.",
                              "mapped_requirement_ids": [10]
                            }
                          ],
                          "warnings": [],
                          "generation_status": "SUCCEEDED"
                        }
                        """));

        PlanningWbsHttpClient client = createClient();
        var response = client.generateWbs(request());

        var recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/v1/planning/wbs/generate");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"project_info\"")
                .contains("\"requirement_candidates\"")
                .contains("\"function_name\":\"로그인\"")
                .doesNotContain("\"requirements\":");
        assertThat(response.projectName()).isEqualTo("프로젝트");
        assertThat(response.wbsItems()).hasSize(1);
        assertThat(response.wbsItems().getFirst().itemType()).isEqualTo("PHASE");
    }

    @Test
    void rejectsNativeResponseWithoutWbsItems() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_name": "프로젝트",
                          "methodology": [],
                          "wbs_items": [],
                          "warnings": [],
                          "generation_status": "PARTIAL"
                        }
                        """));

        assertThatThrownBy(() -> createClient().generateWbs(request()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                            assertThat(exception.getCode()).isEqualTo("INVALID_PLANNING_WBS_RESPONSE");
                        }
                );
    }

    @Test
    void logsSanitizedAiValidationErrorWithoutRequestInput(CapturedOutput output) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "detail": [
                            {
                              "type": "missing",
                              "loc": ["body", "project_info"],
                              "msg": "Field required",
                              "input": {
                                "project_name": "sensitive-project-name"
                              }
                            }
                          ]
                        }
                        """));

        assertThatThrownBy(() -> createClient().generateWbs(request()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PLANNING_WBS_CLIENT_ERROR")
                );

        assertThat(output.getOut())
                .contains("status=422")
                .contains("path=/api/v1/planning/wbs/generate")
                .contains("type=missing")
                .contains("loc=/body/project_info")
                .contains("msg=Field required")
                .doesNotContain("sensitive-project-name");
    }

    @Test
    void logsAiNotFoundSeparatelyFromValidationError(CapturedOutput output) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"detail":"Not Found"}
                        """));

        assertThatThrownBy(() -> createClient().generateWbs(request()))
                .isInstanceOf(ApiException.class);

        assertThat(output.getOut())
                .contains("status=404")
                .contains("path=/api/v1/planning/wbs/generate")
                .contains("detail=Not Found")
                .doesNotContain("status=422");
    }

    private PlanningWbsGenerationRequest request() {
        return new PlanningWbsGenerationRequest(
                new PlanningWbsGenerationRequest.ProjectInfo(
                        "프로젝트",
                        "목표",
                        null,
                        null,
                        null,
                        List.of("로그인"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of(new PlanningWbsGenerationRequest.RequirementData(
                        10L,
                        "로그인",
                        "사용자는 로그인할 수 있어야 한다.",
                        "FUNCTIONAL",
                        "HIGH",
                        null,
                        null,
                        null,
                        null,
                        "requirements.txt",
                        "로그인 요구사항"
                )),
                List.of("요구사항 분석")
        );
    }

    private PlanningWbsHttpClient createClient() {
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());
        properties.setWbsPath("/api/v1/planning/wbs/generate");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(5));

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new PlanningWbsHttpClient(
                restClient,
                properties,
                new ObjectMapper().findAndRegisterModules()
        );
    }
}
