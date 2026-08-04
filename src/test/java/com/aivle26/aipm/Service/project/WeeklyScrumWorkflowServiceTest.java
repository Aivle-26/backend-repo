package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.weekly.WeeklyScrumWorkflowModels;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.WeeklyScrumReport;
import com.aivle26.aipm.Entity.project.WeeklyScrumSubmission;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumReportRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumSubmissionRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Service.project.weekly.WeeklyScrumAnalysisAssembler;
import com.aivle26.aipm.Service.project.weekly.WeeklyScrumReviewManager;
import com.aivle26.aipm.client.ai.WeeklyScrumAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyScrumWorkflowServiceTest {
    private static final Long PROJECT_ID = 101L;
    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 3);

    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock WeeklyScrumSubmissionRepository submissionRepository;
    @Mock WeeklyScrumReportRepository reportRepository;
    @Mock ProjectTaskAssignmentRepository assignmentRepository;
    @Mock ProjectRequirementRepository requirementRepository;
    @Mock UserCapabilityProfileRepository capabilityProfileRepository;
    @Mock WeeklyScrumAiClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private WeeklyScrumWorkflowService service;
    private Project project;
    private ProjectMember member;

    @BeforeEach
    void setUp() {
        service = new WeeklyScrumWorkflowService(
                authorizationService,
                projectRepository,
                reportRepository,
                aiClient,
                objectMapper,
                new WeeklyScrumAnalysisAssembler(
                        projectMemberRepository,
                        submissionRepository,
                        assignmentRepository,
                        requirementRepository,
                        capabilityProfileRepository,
                        objectMapper
                ),
                new WeeklyScrumReviewManager(projectMemberRepository, objectMapper)
        );
        project = new Project();
        project.setId(PROJECT_ID);
        project.setName("AIPM");
        User user = new User();
        user.setEmployeeNumber("STAFF001");
        user.setName("Backend Developer");
        user.setEmail("staff001@example.com");
        member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setActive(true);
        member.setAvailableHoursPerWeek(32.0);
        member.setSelectedBy("PM001");
    }

    @Test
    void analyzesThreeStagesAndPreservesFallback() throws Exception {
        WeeklyScrumSubmission submission = new WeeklyScrumSubmission(
                project,
                "STAFF001",
                WEEK_START,
                "API integration completed",
                "Run integration tests",
                "No blockers"
        );
        ReflectionTestUtils.setField(submission, "id", 10L);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reportRepository.findByProjectIdAndWeekStartDate(PROJECT_ID, WEEK_START))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any(WeeklyScrumReport.class)))
                .thenAnswer(invocation -> {
                    WeeklyScrumReport report = invocation.getArgument(0);
                    report.setId(1L);
                    return report;
                });
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of(member));
        when(submissionRepository
                .findByProjectIdAndWeekStartDateOrderByUpdatedAtDescEmployeeNumberAsc(
                        PROJECT_ID,
                        WEEK_START
                )).thenReturn(List.of(submission));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any())).thenReturn(List.of());
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of());
        when(requirementRepository.findAllByFilters(PROJECT_ID, null, null, null, true))
                .thenReturn(List.of());
        when(aiClient.summarize(any())).thenReturn(new WeeklyScrumAiClient.AiCallResult(
                json("""
                        {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                         "fact_summary":{},"team_summary":{},"llm_status":"FALLBACK"}
                        """),
                "req-summary"
        ));
        when(aiClient.review(any())).thenReturn(new WeeklyScrumAiClient.AiCallResult(
                json("""
                        {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                         "review_findings":[],"finding_count":0,"llm_status":"SUCCEEDED"}
                        """),
                "req-review"
        ));
        when(aiClient.recommendNextActions(any())).thenReturn(new WeeklyScrumAiClient.AiCallResult(
                json("""
                        {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                         "next_week_start":"2026-08-10","next_week_end":"2026-08-16",
                         "recommended_next_actions":[],"action_count":0,"llm_status":"SUCCEEDED"}
                        """),
                "req-recommend"
        ));

        var response = service.analyze(
                PROJECT_ID,
                WEEK_START,
                new WeeklyScrumWorkflowModels.AnalyzeRequest("MVP delivery", true)
        );

        ArgumentCaptor<JsonNode> summarizeRequest = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient).summarize(summarizeRequest.capture());
        JsonNode candidate = summarizeRequest.getValue().path("team_members").get(0);
        assertThat(candidate.path("member_id").asText()).isEqualTo("STAFF001");
        assertThat(candidate.path("availability_hours").asDouble()).isEqualTo(32.0);
        assertThat(summarizeRequest.getValue().path("member_updates").get(0)
                .path("completed_tasks").get(0).path("status").asText()).isEqualTo("DONE");
        assertThat(response.status())
                .isEqualTo(WeeklyScrumReport.WorkflowStatus.ACTIONS_RECOMMENDED);
        assertThat(response.llmStatuses().summarize()).isEqualTo("FALLBACK");
    }

    @Test
    void savesCompletePmReviewAndFinalizesReport() throws Exception {
        WeeklyScrumReport report = report(WeeklyScrumReport.WorkflowStatus.ACTIONS_RECOMMENDED);
        report.setSummarizeResponseJson("""
                {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                 "fact_summary":{},"team_summary":{},"llm_status":"SUCCEEDED"}
                """);
        report.setReviewResponseJson("""
                {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                 "review_findings":[{"finding_id":"FIND-1","review_status":"PENDING"}],
                 "finding_count":1,"llm_status":"SUCCEEDED"}
                """);
        report.setRecommendResponseJson("""
                {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                 "next_week_start":"2026-08-10","next_week_end":"2026-08-16",
                 "recommended_next_actions":[{"action_id":"ACT-1","owner_id":"STAFF001",
                   "owner":"Backend Developer","due_date":"2026-08-12","source_finding_ids":["FIND-1"],
                   "review_status":"PENDING"}],"action_count":1,"llm_status":"SUCCEEDED"}
                """);
        when(reportRepository.findByProjectIdAndWeekStartDate(PROJECT_ID, WEEK_START))
                .thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any(WeeklyScrumReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of(member));

        var reviewed = service.saveReview(
                PROJECT_ID,
                WEEK_START,
                new WeeklyScrumWorkflowModels.SaveReviewRequest(
                        List.of(new WeeklyScrumWorkflowModels.FindingDecision(
                                "FIND-1", "APPROVED", null, null, null, null
                        )),
                        List.of(new WeeklyScrumWorkflowModels.ActionDecision(
                                "ACT-1", "APPROVED", null, null, null, null,
                                null, null, null, null
                        ))
                )
        );
        assertThat(reviewed.status()).isEqualTo(WeeklyScrumReport.WorkflowStatus.PM_REVIEWING);

        when(aiClient.finalizeReport(any())).thenReturn(new WeeklyScrumAiClient.AiCallResult(
                json("""
                        {"project_id":101,"week_start":"2026-08-03","week_end":"2026-08-09",
                         "included_findings":[],"excluded_findings":[],
                         "included_next_actions":[],"excluded_next_actions":[],
                         "final_report":"# Final report","llm_status":"SUCCEEDED"}
                        """),
                "req-final"
        ));
        var finalized = service.finalizeReport(PROJECT_ID, WEEK_START);

        assertThat(finalized.status()).isEqualTo(WeeklyScrumReport.WorkflowStatus.FINALIZED);
        assertThat(finalized.finalReport()).isEqualTo("# Final report");
        ArgumentCaptor<JsonNode> finalizeRequest = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient).finalizeReport(finalizeRequest.capture());
        assertThat(finalizeRequest.getValue().path("source_finding_count").asInt()).isEqualTo(1);
        assertThat(finalizeRequest.getValue().path("source_action_count").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsIncompletePmDecisions() {
        WeeklyScrumReport report = report(WeeklyScrumReport.WorkflowStatus.ACTIONS_RECOMMENDED);
        report.setReviewResponseJson("""
                {"review_findings":[{"finding_id":"FIND-1"}]}
                """);
        report.setRecommendResponseJson("""
                {"recommended_next_actions":[]}
                """);
        when(reportRepository.findByProjectIdAndWeekStartDate(PROJECT_ID, WEEK_START))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.saveReview(
                PROJECT_ID,
                WEEK_START,
                new WeeklyScrumWorkflowModels.SaveReviewRequest(List.of(), List.of())
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo("INVALID_WEEKLY_SCRUM_PM_REVIEW");
                }
        );
    }

    private WeeklyScrumReport report(WeeklyScrumReport.WorkflowStatus status) {
        WeeklyScrumReport report = new WeeklyScrumReport(project, WEEK_START, "Goal", true);
        report.setId(1L);
        report.setWorkflowStatus(status);
        return report;
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
