package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Dto.project.UiMockupGenerateRequest;
import com.aivle26.aipm.Exception.ApiException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningResourceHttpClientTest {

    private MockWebServer server;
    private PlanningResourceHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setResourcePath("/api/v1/planning/resources/recommend");
        properties.setOrganizationChartPath(
                "/api/v1/planning/resources/organization-chart/generate"
        );
        properties.setUiMockupPath("/api/v1/planning/ui-mockup/generate");
        properties.setUiMockupAssessmentPath("/api/v1/planning/ui-mockup/assess");
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

    @Test
    void decodesValidOrganizationChartJpeg() throws Exception {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        enqueueOrganizationChart(Base64.getEncoder().encodeToString(jpeg));

        var response = client.generateOrganizationChart(organizationRequest());
        var recorded = server.takeRequest();

        assertThat(recorded.getPath()).isEqualTo(
                "/api/v1/planning/resources/organization-chart/generate"
        );
        assertThat(recorded.getBody().readUtf8())
                .contains("\"project_manager_member_id\":1")
                .contains("\"project_name\":\"Test Project\"");
        assertThat(response.image()).containsExactly(jpeg);
        assertThat(response.response().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsInvalidOrganizationChartBase64() {
        enqueueOrganizationChart("%%%not-base64%%%");

        assertApiError("INVALID_ORGANIZATION_CHART_BASE64");
    }

    @Test
    void rejectsEmptyOrganizationChartImage() {
        enqueueOrganizationChart("   ");

        assertApiError("EMPTY_ORGANIZATION_CHART_IMAGE");
    }

    @Test
    void rejectsOversizedOrganizationChartImage() {
        String oversized = "A".repeat(
                ((PlanningResourceHttpClient.MAX_ORGANIZATION_CHART_BYTES + 2) / 3) * 4 + 8
        );
        enqueueOrganizationChart(oversized);

        assertApiError("ORGANIZATION_CHART_IMAGE_TOO_LARGE");
    }

    @Test
    void mapsOrganizationChartClientAndServerErrors() {
        server.enqueue(new MockResponse().setResponseCode(422));
        assertApiError("ORGANIZATION_CHART_AI_CLIENT_ERROR");

        server.enqueue(new MockResponse().setResponseCode(500));
        assertApiError("ORGANIZATION_CHART_AI_SERVER_ERROR");
    }

    @Test
    void mapsOrganizationChartConnectionFailure() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        assertApiError("ORGANIZATION_CHART_AI_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 11, 12})
    void decodesValidUiMockupJpegAndUsesContractPath(int screenCount) throws Exception {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        enqueueUiMockup(Base64.getEncoder().encodeToString(jpeg), screenCount);

        var response = client.generateUiMockup(uiMockupRequest());
        var recorded = server.takeRequest();

        assertThat(recorded.getPath()).isEqualTo("/api/v1/planning/ui-mockup/generate");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"project_title\":\"Test Project\"")
                .contains("\"confirmed_requirements\"");
        assertThat(response.image()).containsExactly(jpeg);
    }

    @Test
    void rejectsUiMockupWithEmptyScreens() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        enqueueUiMockup(Base64.getEncoder().encodeToString(jpeg), 0);

        assertThatThrownBy(() -> client.generateUiMockup(uiMockupRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_UI_MOCKUP_RESPONSE");
    }

    @Test
    void rejectsUiMockupWithMoreThanTwelveScreens() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        enqueueUiMockup(Base64.getEncoder().encodeToString(jpeg), 13);

        assertThatThrownBy(() -> client.generateUiMockup(uiMockupRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_UI_MOCKUP_RESPONSE");
    }

    @Test
    void rejectsInvalidUiMockupJpeg() {
        enqueueUiMockup(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> client.generateUiMockup(uiMockupRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_UI_MOCKUP_RESPONSE");
    }

    @Test
    void assessesUiMockupNecessityUsingSnakeCaseAiContract() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_id": 101,
                          "decision": "REQUIRED",
                          "reason": "핵심 사용자 흐름을 검증할 화면 설계가 필요합니다.",
                          "evidence_requirement_ids": [1],
                          "candidate_screens": ["대시보드", "로그인"]
                        }
                        """));

        var response = client.assessUiMockup(uiMockupRequest());
        var recorded = server.takeRequest();

        assertThat(recorded.getPath()).isEqualTo("/api/v1/planning/ui-mockup/assess");
        assertThat(recorded.getBody().readUtf8())
                .contains("\"project_id\":101")
                .contains("\"confirmed_requirements\"");
        assertThat(response.decision().name()).isEqualTo("REQUIRED");
        assertThat(response.evidenceRequirementIds()).containsExactly(1L);
        assertThat(response.candidateScreens()).containsExactly("대시보드", "로그인");
    }

    @Test
    void rejectsAssessmentEvidenceOutsideConfirmedRequirements() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_id": 101,
                          "decision": "RECOMMENDED",
                          "reason": "화면 검토를 권장합니다.",
                          "evidence_requirement_ids": [999],
                          "candidate_screens": ["대시보드"]
                        }
                        """));

        assertThatThrownBy(() -> client.assessUiMockup(uiMockupRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_UI_MOCKUP_ASSESSMENT_RESPONSE");
    }

    private void enqueueOrganizationChart(String imageBase64) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "organization": {
                            "project_id": 101,
                            "project_manager": 1,
                            "teams": [],
                            "role_gaps": [],
                            "unassigned_wbs_ids": [],
                            "warnings": ["역량 정보가 없어 자동 배정에서 제외된 팀원이 1명 있습니다."],
                            "generated_at": "2026-08-05T10:00:00Z"
                          },
                          "file_name": "project-101-organization-chart.jpg",
                          "content_type": "image/jpeg",
                          "image_base64": "%s",
                          "width": 1200,
                          "height": 900
                        }
                        """.formatted(imageBase64)));
    }

    private void enqueueUiMockup(String imageBase64) {
        enqueueUiMockup(imageBase64, 1);
    }

    private void enqueueUiMockup(String imageBase64, int screenCount) {
        String screens = String.join(",", java.util.Collections.nCopies(
                screenCount,
                "{\"screen_name\":\"Dashboard\"}"
        ));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "project_id": 101,
                          "mockup": {
                            "project_title": "Test Project",
                            "design_summary": "Summary",
                            "screens": [%s]
                          },
                          "file_name": "project-101-ui-mockup.jpg",
                          "content_type": "image/jpeg",
                          "image_base64": "%s",
                          "width": 1920,
                          "height": 1080
                        }
                        """.formatted(screens, imageBase64)));
    }

    private void assertApiError(String code) {
        assertThatThrownBy(() -> client.generateOrganizationChart(organizationRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private OrganizationChartGenerateRequest organizationRequest() {
        PlanningResourceRecommendRequest planningRequest = new PlanningResourceRecommendRequest(
                101L,
                "Test Project",
                request().wbsTasks(),
                request().projectMembers()
        );
        return new OrganizationChartGenerateRequest(
                planningRequest,
                new OrganizationChartGenerateRequest.OrganizationMetadata(1L, List.of())
        );
    }

    private UiMockupGenerateRequest uiMockupRequest() {
        return new UiMockupGenerateRequest(
                101L,
                "Test Project",
                "Project description",
                List.of(new UiMockupGenerateRequest.ConfirmedRequirement(
                        1L,
                        "Dashboard",
                        "Show project status",
                        "FUNCTIONAL",
                        "HIGH"
                ))
        );
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
