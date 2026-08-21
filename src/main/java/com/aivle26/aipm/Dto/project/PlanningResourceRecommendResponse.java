package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlanningResourceRecommendResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("required_staffing") List<RequiredStaffing> requiredStaffing,
        List<Assignment> assignments,
        @JsonProperty("total_estimated_person_days") double totalEstimatedPersonDays,
        @JsonProperty("total_estimated_hours") double totalEstimatedHours,
        @JsonProperty("total_estimated_mm") double totalEstimatedMm,
        @JsonProperty("unassigned_wbs_ids") List<Long> unassignedWbsIds,
        List<String> warnings,
        @JsonProperty("llm_status") String llmStatus
) {
    public record RequiredStaffing(
            @JsonProperty("role_code") String roleCode,
            @JsonProperty("required_headcount") int requiredHeadcount,
            @JsonProperty("available_candidate_count") int availableCandidateCount,
            @JsonProperty("shortage_count") int shortageCount,
            @JsonProperty("estimated_person_days") double estimatedPersonDays,
            @JsonProperty("estimated_mm") double estimatedMm
    ) {
    }

    public record Assignment(
            @JsonProperty("wbs_id") Long wbsId,
            @JsonProperty("required_role_code") String requiredRoleCode,
            @JsonProperty("required_skills") List<RequiredSkill> requiredSkills,
            @JsonProperty("estimated_person_days") double estimatedPersonDays,
            @JsonProperty("estimated_hours") double estimatedHours,
            @JsonProperty("estimated_mm") double estimatedMm,
            @JsonProperty("required_headcount") int requiredHeadcount,
            @JsonProperty("recommended_members") List<RecommendedMember> recommendedMembers,
            @JsonProperty("recommendation_reason") String recommendationReason
    ) {
    }

    public record RequiredSkill(
            @JsonProperty("skill_code") String skillCode,
            @JsonProperty("minimum_proficiency_level") int minimumProficiencyLevel
    ) {
    }

    public record RecommendedMember(
            @JsonProperty("project_member_id") Long projectMemberId,
            @JsonProperty("recommendation_score") double recommendationScore,
            @JsonProperty("assigned_hours") double assignedHours,
            @JsonProperty("remaining_available_hours") double remainingAvailableHours
    ) {
    }
}
