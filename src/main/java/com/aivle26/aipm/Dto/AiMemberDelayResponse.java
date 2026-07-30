package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버(FastAPI)의 MemberDelayResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiMemberDelayResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("analyzed_member_count") int analyzedMemberCount,
        @JsonProperty("high_risk_member_count") int highRiskMemberCount,
        @JsonProperty("member_results") List<AiMemberResult> memberResults
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiMemberResult(
            @JsonProperty("member_id") Long memberId,
            @JsonProperty("member_name") String memberName,
            @JsonProperty("completion_rate") double completionRate,
            @JsonProperty("overdue_rate") double overdueRate,
            @JsonProperty("delay_score") int delayScore,
            @JsonProperty("risk_level") String riskLevel,
            @JsonProperty("reasons") List<String> reasons,
            @JsonProperty("recommended_action") String recommendedAction
    ) {
    }
}
