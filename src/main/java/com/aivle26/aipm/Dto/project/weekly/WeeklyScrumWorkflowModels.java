package com.aivle26.aipm.Dto.project.weekly;

import com.aivle26.aipm.Entity.project.WeeklyScrumReport;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class WeeklyScrumWorkflowModels {
    private WeeklyScrumWorkflowModels() {
    }

    public record AnalyzeRequest(
            @Size(max = 1000) String sprintGoal,
            Boolean enableLlm
    ) {
        public boolean useLlm() {
            return enableLlm == null || enableLlm;
        }
    }

    public record SaveReviewRequest(
            @NotNull @Valid List<@Valid FindingDecision> findings,
            @NotNull @Valid List<@Valid ActionDecision> actions
    ) {
    }

    public record FindingDecision(
            @NotBlank String findingId,
            @NotBlank String reviewStatus,
            @Size(max = 2000) String reviewComment,
            @Size(max = 500) String modifiedTitle,
            @Size(max = 4000) String modifiedDescription,
            @Size(max = 4000) String modifiedAction
    ) {
    }

    public record ActionDecision(
            @NotBlank String actionId,
            @NotBlank String reviewStatus,
            @Size(max = 2000) String reviewComment,
            @Size(max = 500) String modifiedTitle,
            @Size(max = 50) String modifiedOwnerId,
            @Size(max = 100) String modifiedOwner,
            LocalDate modifiedDueDate,
            @Size(max = 30) String modifiedPriority,
            @Size(max = 2000) String modifiedDoneCondition,
            @Size(max = 4000) String modifiedReason
    ) {
    }

    public record ReportResponse(
            Long id,
            Long projectId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            String sprintGoal,
            boolean enableLlm,
            WeeklyScrumReport.WorkflowStatus status,
            JsonNode summary,
            JsonNode review,
            JsonNode recommendation,
            JsonNode pmReview,
            JsonNode finalResult,
            String finalReport,
            LlmStatuses llmStatuses,
            Failure failure,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record LlmStatuses(
            String summarize,
            String review,
            String recommend,
            String finalizeStatus
    ) {
    }

    public record Failure(String code, String message) {
    }
}
