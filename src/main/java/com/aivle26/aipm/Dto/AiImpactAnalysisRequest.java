package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버(FastAPI)의 ImpactAssessmentRequest.
 * 필드명이 snake_case라 @JsonProperty로 명시한다.
 * 엔드포인트: POST {base-url}/api/v1/risk/impact-assessment
 *
 * <p>기존 수동 입력 수치는 LLM 미사용/실패 시 fallback 입력으로 그대로 유지한다.
 * wbsTasks와 useLlm=true가 함께 전달되면 AI가 영향 업무 수·추가 작업일 등을
 * 자동 산출한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiImpactAnalysisRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("requirement_id") Long requirementId,
        @JsonProperty("change_title") String changeTitle,
        @JsonProperty("change_description") String changeDescription,

        // --- 수동 입력 수치 (fallback) ---
        @JsonProperty("affected_task_count") int affectedTaskCount,
        @JsonProperty("affected_member_count") int affectedMemberCount,
        @JsonProperty("remaining_days") int remainingDays,
        @JsonProperty("additional_work_days") int additionalWorkDays,
        @JsonProperty("scope_changed") boolean scopeChanged,
        @JsonProperty("database_changed") boolean databaseChanged,
        @JsonProperty("api_changed") boolean apiChanged,
        @JsonProperty("ui_changed") boolean uiChanged,

        // --- AI 자동 산출용 컨텍스트 ---
        @JsonProperty("use_llm") boolean useLlm,
        @JsonProperty("wbs_tasks") List<WbsTask> wbsTasks,
        /** 프로젝트 종료일(ISO, yyyy-MM-dd). 남은 일정 자동 계산에 사용. */
        @JsonProperty("project_end_date") String projectEndDate
) {

    /** AI 서버 ImpactWBSTask에 대응하는 확정 WBS 태스크. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WbsTask(
            @JsonProperty("task_id") Long taskId,
            @JsonProperty("task_name") String taskName,
            @JsonProperty("description") String description,
            @JsonProperty("assignee") String assignee,
            @JsonProperty("status") String status,
            @JsonProperty("estimated_days") int estimatedDays
    ) {
    }
}
