package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버(FastAPI)의 ImpactAssessmentResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImpactAnalysisResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("requirement_id") Long requirementId,
        @JsonProperty("impact_score") int impactScore,
        @JsonProperty("impact_level") String impactLevel,
        @JsonProperty("schedule_impact_score") int scheduleImpactScore,
        @JsonProperty("scope_impact_score") int scopeImpactScore,
        @JsonProperty("resource_impact_score") int resourceImpactScore,
        @JsonProperty("technical_impact_score") int technicalImpactScore,
        @JsonProperty("risk_factors") List<String> riskFactors,
        @JsonProperty("recommended_actions") List<String> recommendedActions,

        // --- AI 산출 결과 (프론트 폼 자동 입력용) ---
        @JsonProperty("llm_status") String llmStatus,
        @JsonProperty("ai_summary") String aiSummary,
        @JsonProperty("affected_task_count") int affectedTaskCount,
        @JsonProperty("affected_member_count") int affectedMemberCount,
        @JsonProperty("remaining_days") int remainingDays,
        @JsonProperty("additional_work_days") int additionalWorkDays,
        @JsonProperty("scope_changed") boolean scopeChanged,
        @JsonProperty("database_changed") boolean databaseChanged,
        @JsonProperty("api_changed") boolean apiChanged,
        @JsonProperty("ui_changed") boolean uiChanged,
        @JsonProperty("affected_tasks") List<AffectedTask> affectedTasks
) {

    /** AI가 식별한 영향 태스크 한 건. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AffectedTask(
            @JsonProperty("task_id") Long taskId,
            @JsonProperty("task_name") String taskName,
            @JsonProperty("impact_type") String impactType,
            @JsonProperty("additional_work_days") int additionalWorkDays,
            @JsonProperty("reason") String reason
    ) {
    }
}
