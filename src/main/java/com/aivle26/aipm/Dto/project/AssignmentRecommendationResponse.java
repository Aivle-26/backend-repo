package com.aivle26.aipm.Dto.project;

import java.util.List;

public record AssignmentRecommendationResponse(
        Long projectId,
        CandidateMode candidateMode,
        List<Candidate> candidates,
        List<PlanningResourceRecommendResponse.RequiredStaffing> requiredStaffing,
        List<Assignment> assignments,
        double totalEstimatedPersonDays,
        double totalEstimatedHours,
        double totalEstimatedMm,
        List<Long> unassignedWbsIds,
        List<String> warnings,
        String llmStatus
) {
    public enum CandidateMode {
        ALL,
        SELECTED
    }

    public record Candidate(
            String employeeNumber,
            String name,
            String email,
            double availableHoursPerWeek
    ) {
    }

    public record Assignment(
            Long wbsId,
            String wbsName,
            String requiredRoleCode,
            List<PlanningResourceRecommendResponse.RequiredSkill> requiredSkills,
            double estimatedPersonDays,
            double estimatedHours,
            double estimatedMm,
            int requiredHeadcount,
            List<RecommendedMember> recommendedMembers,
            String recommendationReason
    ) {
    }

    public record RecommendedMember(
            String employeeNumber,
            String name,
            String email,
            double recommendationScore,
            double assignedHours,
            double remainingAvailableHours
    ) {
    }
}
