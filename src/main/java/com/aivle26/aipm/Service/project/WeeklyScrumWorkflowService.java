package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.weekly.WeeklyScrumWorkflowModels;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.WeeklyScrumReport;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumReportRepository;
import com.aivle26.aipm.Service.project.weekly.WeeklyScrumAnalysisAssembler;
import com.aivle26.aipm.Service.project.weekly.WeeklyScrumReviewManager;
import com.aivle26.aipm.client.ai.WeeklyScrumAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WeeklyScrumWorkflowService {
    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final WeeklyScrumReportRepository reportRepository;
    private final WeeklyScrumAiClient aiClient;
    private final ObjectMapper objectMapper;
    private final WeeklyScrumAnalysisAssembler analysisAssembler;
    private final WeeklyScrumReviewManager reviewManager;

    public WeeklyScrumWorkflowModels.ReportResponse analyze(
            Long projectId,
            LocalDate weekStartDate,
            WeeklyScrumWorkflowModels.AnalyzeRequest request
    ) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectPm(projectId);
        Project project = requireProject(projectId);
        WeeklyScrumReport report = reportRepository
                .findByProjectIdAndWeekStartDate(projectId, weekStartDate)
                .orElseGet(() -> new WeeklyScrumReport(
                        project,
                        weekStartDate,
                        normalize(request.sprintGoal()),
                        request.useLlm()
                ));
        report.restart(normalize(request.sprintGoal()), request.useLlm());
        report = reportRepository.saveAndFlush(report);

        try {
            WeeklyScrumAiClient.AiCallResult summarize = aiClient.summarize(
                    analysisAssembler.buildSummarizeRequest(report)
            );
            validateSummarizeResponse(report, summarize.body());
            report.setSummarizeResponseJson(writeJson(summarize.body()));
            report.setSummarizeLlmStatus(llmStatus(summarize.body()));
            report.setLastRequestId(summarize.requestId());
            report.setWorkflowStatus(WeeklyScrumReport.WorkflowStatus.SUMMARIZED);
            report = reportRepository.saveAndFlush(report);

            WeeklyScrumAiClient.AiCallResult review = aiClient.review(
                    analysisAssembler.buildReviewRequest(report, summarize.body())
            );
            validateReviewResponse(report, review.body());
            report.setReviewResponseJson(writeJson(review.body()));
            report.setReviewLlmStatus(llmStatus(review.body()));
            report.setLastRequestId(review.requestId());
            report.setWorkflowStatus(WeeklyScrumReport.WorkflowStatus.REVIEWED);
            report = reportRepository.saveAndFlush(report);

            WeeklyScrumAiClient.AiCallResult recommendation = aiClient.recommendNextActions(
                    analysisAssembler.buildRecommendRequest(
                            report,
                            summarize.body(),
                            review.body()
                    )
            );
            validateRecommendResponse(report, recommendation.body());
            report.setRecommendResponseJson(writeJson(recommendation.body()));
            report.setRecommendLlmStatus(llmStatus(recommendation.body()));
            report.setLastRequestId(recommendation.requestId());
            report.setWorkflowStatus(WeeklyScrumReport.WorkflowStatus.ACTIONS_RECOMMENDED);
            return toResponse(reportRepository.saveAndFlush(report));
        } catch (ApiException exception) {
            fail(report, exception.getCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            fail(report, "WEEKLY_SCRUM_ANALYSIS_FAILED", "주간 보고서 분석 중 오류가 발생했습니다.");
            throw exception;
        }
    }

    public WeeklyScrumWorkflowModels.ReportResponse saveReview(
            Long projectId,
            LocalDate weekStartDate,
            WeeklyScrumWorkflowModels.SaveReviewRequest request
    ) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectPm(projectId);
        WeeklyScrumReport report = requireReport(projectId, weekStartDate);
        requireStatus(
                report,
                Set.of(
                        WeeklyScrumReport.WorkflowStatus.ACTIONS_RECOMMENDED,
                        WeeklyScrumReport.WorkflowStatus.PM_REVIEWING,
                        WeeklyScrumReport.WorkflowStatus.FAILED
                ),
                "WEEKLY_SCRUM_REVIEW_NOT_READY"
        );
        ObjectNode pmReview = reviewManager.apply(
                projectId,
                report,
                readJson(report.getReviewResponseJson(), "review response"),
                readJson(report.getRecommendResponseJson(), "recommendation response"),
                request
        );
        report.setPmReviewJson(writeJson(pmReview));
        report.setWorkflowStatus(WeeklyScrumReport.WorkflowStatus.PM_REVIEWING);
        report.setFailureCode(null);
        report.setFailureMessage(null);
        return toResponse(reportRepository.saveAndFlush(report));
    }

    public WeeklyScrumWorkflowModels.ReportResponse finalizeReport(
            Long projectId,
            LocalDate weekStartDate
    ) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectPm(projectId);
        WeeklyScrumReport report = requireReport(projectId, weekStartDate);
        if (report.getWorkflowStatus() == WeeklyScrumReport.WorkflowStatus.FINALIZED) {
            return toResponse(report);
        }
        requireStatus(
                report,
                Set.of(
                        WeeklyScrumReport.WorkflowStatus.PM_REVIEWING,
                        WeeklyScrumReport.WorkflowStatus.FAILED
                ),
                "WEEKLY_SCRUM_PM_REVIEW_REQUIRED"
        );

        try {
            WeeklyScrumAiClient.AiCallResult result = aiClient.finalizeReport(
                    buildFinalizeRequest(report)
            );
            validateFinalizeResponse(report, result.body());
            report.setFinalizeResponseJson(writeJson(result.body()));
            report.setFinalReport(result.body().path("final_report").asText());
            report.setFinalizeLlmStatus(llmStatus(result.body()));
            report.setLastRequestId(result.requestId());
            report.setWorkflowStatus(WeeklyScrumReport.WorkflowStatus.FINALIZED);
            report.setFailureCode(null);
            report.setFailureMessage(null);
            return toResponse(reportRepository.saveAndFlush(report));
        } catch (ApiException exception) {
            fail(report, exception.getCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            fail(report, "WEEKLY_SCRUM_FINALIZE_FAILED", "최종 주간 보고서 생성 중 오류가 발생했습니다.");
            throw exception;
        }
    }

    public WeeklyScrumWorkflowModels.ReportResponse getAnalysis(
            Long projectId,
            LocalDate weekStartDate
    ) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectPm(projectId);
        return toResponse(requireReport(projectId, weekStartDate));
    }

    public WeeklyScrumWorkflowModels.ReportResponse getFinalReport(
            Long projectId,
            LocalDate weekStartDate
    ) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectAccess(projectId);
        WeeklyScrumReport report = requireReport(projectId, weekStartDate);
        requireStatus(
                report,
                Set.of(WeeklyScrumReport.WorkflowStatus.FINALIZED),
                "WEEKLY_SCRUM_REPORT_NOT_FINALIZED"
        );
        return toResponse(report);
    }

    private ObjectNode buildFinalizeRequest(WeeklyScrumReport report) {
        JsonNode summarize = readJson(report.getSummarizeResponseJson(), "summary response");
        JsonNode pmReview = readJson(report.getPmReviewJson(), "PM review");
        ObjectNode request = objectMapper.createObjectNode();
        request.put("project_id", report.getProject().getId());
        request.put("project_name", report.getProject().getName());
        request.put("week_start", report.getWeekStartDate().toString());
        request.put("week_end", report.getWeekEndDate().toString());
        request.set("fact_summary", requireObject(summarize, "fact_summary"));
        request.set("team_summary", requireObject(summarize, "team_summary"));
        request.set("reviewed_findings", requireArray(pmReview, "reviewed_findings"));
        request.set(
                "recommended_next_actions",
                requireArray(pmReview, "recommended_next_actions")
        );
        request.put("source_finding_count", pmReview.path("source_finding_count").asInt());
        request.put("source_action_count", pmReview.path("source_action_count").asInt());
        request.put("enable_llm", report.isEnableLlm());
        return request;
    }

    private void validateSummarizeResponse(WeeklyScrumReport report, JsonNode response) {
        validateCommonResponse(report, response);
        requireObject(response, "fact_summary");
        requireObject(response, "team_summary");
    }

    private void validateReviewResponse(WeeklyScrumReport report, JsonNode response) {
        validateCommonResponse(report, response);
        ArrayNode findings = requireArray(response, "review_findings");
        if (response.path("finding_count").asInt(-1) != findings.size()) {
            throw invalidAiResponse("finding_count가 review_findings 길이와 일치하지 않습니다.");
        }
    }

    private void validateRecommendResponse(WeeklyScrumReport report, JsonNode response) {
        validateCommonResponse(report, response);
        ArrayNode actions = requireArray(response, "recommended_next_actions");
        if (response.path("action_count").asInt(-1) != actions.size()) {
            throw invalidAiResponse("action_count가 recommended_next_actions 길이와 일치하지 않습니다.");
        }
        LocalDate expectedStart = report.getWeekEndDate().plusDays(1);
        LocalDate expectedEnd = report.getWeekEndDate().plusDays(7);
        if (!expectedStart.toString().equals(response.path("next_week_start").asText())
                || !expectedEnd.toString().equals(response.path("next_week_end").asText())) {
            throw invalidAiResponse("AI가 반환한 다음 주 기간이 요청 기간과 일치하지 않습니다.");
        }
    }

    private void validateFinalizeResponse(WeeklyScrumReport report, JsonNode response) {
        validateCommonResponse(report, response);
        requireArray(response, "included_findings");
        requireArray(response, "excluded_findings");
        requireArray(response, "included_next_actions");
        requireArray(response, "excluded_next_actions");
        if (normalize(response.path("final_report").asText(null)) == null) {
            throw invalidAiResponse("AI 최종 보고서가 비어 있습니다.");
        }
    }

    private void validateCommonResponse(WeeklyScrumReport report, JsonNode response) {
        if (response.path("project_id").asLong(-1) != report.getProject().getId()
                || !report.getWeekStartDate().toString().equals(response.path("week_start").asText())
                || !report.getWeekEndDate().toString().equals(response.path("week_end").asText())) {
            throw invalidAiResponse("AI 응답의 프로젝트 또는 주차가 요청과 일치하지 않습니다.");
        }
        llmStatus(response);
    }

    private String llmStatus(JsonNode response) {
        String status = response.path("llm_status").asText();
        if (!"SUCCEEDED".equals(status) && !"FALLBACK".equals(status)) {
            throw invalidAiResponse("AI 응답의 llm_status가 올바르지 않습니다.");
        }
        return status;
    }

    private WeeklyScrumWorkflowModels.ReportResponse toResponse(WeeklyScrumReport report) {
        WeeklyScrumWorkflowModels.Failure failure = report.getFailureCode() == null
                ? null
                : new WeeklyScrumWorkflowModels.Failure(
                        report.getFailureCode(),
                        report.getFailureMessage()
                );
        return new WeeklyScrumWorkflowModels.ReportResponse(
                report.getId(),
                report.getProject().getId(),
                report.getWeekStartDate(),
                report.getWeekEndDate(),
                report.getSprintGoal(),
                report.isEnableLlm(),
                report.getWorkflowStatus(),
                readNullableJson(report.getSummarizeResponseJson()),
                readNullableJson(report.getReviewResponseJson()),
                readNullableJson(report.getRecommendResponseJson()),
                readNullableJson(report.getPmReviewJson()),
                readNullableJson(report.getFinalizeResponseJson()),
                report.getFinalReport(),
                new WeeklyScrumWorkflowModels.LlmStatuses(
                        report.getSummarizeLlmStatus(),
                        report.getReviewLlmStatus(),
                        report.getRecommendLlmStatus(),
                        report.getFinalizeLlmStatus()
                ),
                failure,
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private WeeklyScrumReport requireReport(Long projectId, LocalDate weekStartDate) {
        return reportRepository.findByProjectIdAndWeekStartDate(projectId, weekStartDate)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WEEKLY_SCRUM_REPORT_NOT_FOUND",
                        "주간 보고서 분석 결과를 찾을 수 없습니다."
                ));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."
                ));
    }

    private void requireStatus(
            WeeklyScrumReport report,
            Set<WeeklyScrumReport.WorkflowStatus> allowed,
            String code
    ) {
        if (!allowed.contains(report.getWorkflowStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    code,
                    "현재 주간 보고서 상태에서는 요청을 처리할 수 없습니다. status="
                            + report.getWorkflowStatus()
            );
        }
    }

    private void fail(WeeklyScrumReport report, String code, String message) {
        report.fail(code == null ? "WEEKLY_SCRUM_AI_ERROR" : code, message);
        reportRepository.saveAndFlush(report);
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "WEEKLY_SCRUM_SERIALIZATION_FAILED",
                    "주간 보고서 결과를 저장하지 못했습니다.",
                    exception
            );
        }
    }

    private JsonNode readJson(String value, String label) {
        JsonNode result = readNullableJson(value);
        if (result == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WEEKLY_SCRUM_STAGE_DATA_MISSING",
                    "저장된 " + label + " 데이터가 없습니다."
            );
        }
        return result;
    }

    private JsonNode readNullableJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_WEEKLY_SCRUM_STORED_DATA",
                    "저장된 주간 보고서 데이터 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }

    private ObjectNode requireObject(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        throw invalidAiResponse(fieldName + " 객체가 없습니다.");
    }

    private ArrayNode requireArray(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value instanceof ArrayNode arrayNode) {
            return arrayNode.deepCopy();
        }
        throw invalidAiResponse(fieldName + " 배열이 없습니다.");
    }

    private ApiException invalidAiResponse(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_WEEKLY_SCRUM_AI_RESPONSE",
                message
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateMonday(LocalDate weekStartDate) {
        if (weekStartDate == null || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_WEEK_START_DATE",
                    "weekStartDate는 월요일이어야 합니다."
            );
        }
    }
}
