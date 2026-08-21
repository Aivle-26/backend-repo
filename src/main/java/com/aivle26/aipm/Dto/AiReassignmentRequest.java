package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버(FastAPI)의 AssigneeReassignmentRequest.
 * 필드명이 snake_case라 @JsonProperty로 명시한다.
 * 엔드포인트: POST {base-url}/api/v1/risk/assignee-reassignment
 */
public record AiReassignmentRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("task_id") Long taskId,
        @JsonProperty("task_name") String taskName,
        @JsonProperty("required_role") String requiredRole,
        @JsonProperty("required_skills") List<String> requiredSkills,
        @JsonProperty("current_assignee") AiCurrentAssignee currentAssignee,
        @JsonProperty("candidates") List<AiCandidateMember> candidates
) {
    /** AI 서버의 CurrentAssignee. */
    public record AiCurrentAssignee(
            @JsonProperty("member_id") Long memberId,
            @JsonProperty("member_name") String memberName,
            @JsonProperty("skills") List<String> skills,
            @JsonProperty("workload_rate") double workloadRate,
            @JsonProperty("overdue_task_count") int overdueTaskCount
    ) {
    }

    /** AI 서버의 CandidateMember. */
    public record AiCandidateMember(
            @JsonProperty("member_id") Long memberId,
            @JsonProperty("member_name") String memberName,
            @JsonProperty("role") String role,
            @JsonProperty("skills") List<String> skills,
            @JsonProperty("workload_rate") double workloadRate,
            @JsonProperty("overdue_task_count") int overdueTaskCount
    ) {
    }
}
