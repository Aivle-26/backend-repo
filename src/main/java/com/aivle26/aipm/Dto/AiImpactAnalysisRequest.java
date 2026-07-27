package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 서버(FastAPI)의 ImpactAssessmentRequest.
 * 필드명이 snake_case라 @JsonProperty로 명시한다.
 * 엔드포인트: POST {base-url}/api/v1/risk/impact-assessment
 */
public record AiImpactAnalysisRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("requirement_id") Long requirementId,
        @JsonProperty("change_title") String changeTitle,
        @JsonProperty("change_description") String changeDescription,
        @JsonProperty("affected_task_count") int affectedTaskCount,
        @JsonProperty("affected_member_count") int affectedMemberCount,
        @JsonProperty("remaining_days") int remainingDays,
        @JsonProperty("additional_work_days") int additionalWorkDays,
        @JsonProperty("scope_changed") boolean scopeChanged,
        @JsonProperty("database_changed") boolean databaseChanged,
        @JsonProperty("api_changed") boolean apiChanged,
        @JsonProperty("ui_changed") boolean uiChanged
) {
}
