package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버(FastAPI)의 AssigneeReassignmentResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReassignmentResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("task_id") Long taskId,
        @JsonProperty("reassignment_required") boolean reassignmentRequired,
        @JsonProperty("current_assignee_risk_score") int currentAssigneeRiskScore,
        @JsonProperty("current_assignee_risk_level") String currentAssigneeRiskLevel,
        /** 추천 후보가 없으면 AI 서버가 null을 준다. */
        @JsonProperty("recommended_assignee") AiCandidateResult recommendedAssignee,
        @JsonProperty("alternative_candidates") List<AiCandidateResult> alternativeCandidates,
        @JsonProperty("reasons") List<String> reasons
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiCandidateResult(
            @JsonProperty("member_id") Long memberId,
            @JsonProperty("member_name") String memberName,
            @JsonProperty("match_score") int matchScore,
            @JsonProperty("skill_match_rate") double skillMatchRate,
            @JsonProperty("workload_rate") double workloadRate,
            @JsonProperty("overdue_task_count") int overdueTaskCount,
            @JsonProperty("reason") String reason
    ) {
    }
}
