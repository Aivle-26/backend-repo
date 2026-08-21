package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OrganizationChartGenerateRequest(
        @JsonProperty("planning_request") PlanningResourceRecommendRequest planningRequest,
        @JsonProperty("organization_metadata") OrganizationMetadata organizationMetadata
) {
    public record OrganizationMetadata(
            @JsonProperty("project_manager_member_id") Long projectManagerMemberId,
            List<TeamMetadata> teams
    ) {
    }

    public record TeamMetadata(
            @JsonProperty("role_code") String roleCode,
            @JsonProperty("team_name") String teamName,
            @JsonProperty("leader_member_id") Long leaderMemberId,
            @JsonProperty("reports_to_role_code") String reportsToRoleCode,
            @JsonProperty("collaborates_with_role_codes") List<String> collaboratesWithRoleCodes
    ) {
    }
}
