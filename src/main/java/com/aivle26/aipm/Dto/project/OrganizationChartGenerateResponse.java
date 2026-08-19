package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record OrganizationChartGenerateResponse(
        OrganizationView organization,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("image_base64") String imageBase64,
        int width,
        int height
) {
    public record OrganizationView(
            @JsonProperty("project_id") Long projectId,
            @JsonProperty("project_manager") Long projectManager,
            List<OrganizationTeam> teams,
            @JsonProperty("role_gaps") List<OrganizationRoleGap> roleGaps,
            @JsonProperty("unassigned_wbs_ids") List<Long> unassignedWbsIds,
            List<String> warnings,
            @JsonProperty("generated_at") OffsetDateTime generatedAt
    ) {
    }

    public record OrganizationTeam(
            @JsonProperty("team_id") String teamId,
            @JsonProperty("team_name") String teamName,
            @JsonProperty("leader_member_id") Long leaderMemberId,
            @JsonProperty("member_ids") List<Long> memberIds,
            @JsonProperty("primary_roles") List<String> primaryRoles,
            @JsonProperty("secondary_roles") List<String> secondaryRoles,
            @JsonProperty("assigned_wbs_ids") List<Long> assignedWbsIds,
            @JsonProperty("reports_to") String reportsTo,
            @JsonProperty("collaborates_with") List<String> collaboratesWith,
            @JsonProperty("multi_role_members") List<Long> multiRoleMembers
    ) {
    }

    public record OrganizationRoleGap(
            @JsonProperty("role_code") String roleCode,
            @JsonProperty("shortage_count") int shortageCount,
            @JsonProperty("wbs_ids") List<Long> wbsIds
    ) {
    }
}
