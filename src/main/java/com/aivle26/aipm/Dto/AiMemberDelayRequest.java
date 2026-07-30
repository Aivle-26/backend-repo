package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버(FastAPI)의 MemberDelayRequest.
 * 엔드포인트: POST {base-url}/api/v1/risk/member-delay
 */
public record AiMemberDelayRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("members") List<AiMemberTaskStatus> members
) {
    /** AI 서버의 MemberTaskStatus. */
    public record AiMemberTaskStatus(
            @JsonProperty("member_id") Long memberId,
            @JsonProperty("member_name") String memberName,
            @JsonProperty("assigned_task_count") int assignedTaskCount,
            @JsonProperty("completed_task_count") int completedTaskCount,
            @JsonProperty("overdue_task_count") int overdueTaskCount,
            @JsonProperty("in_progress_task_count") int inProgressTaskCount,
            @JsonProperty("average_delay_days") double averageDelayDays,
            @JsonProperty("days_since_last_update") int daysSinceLastUpdate
    ) {
    }
}
