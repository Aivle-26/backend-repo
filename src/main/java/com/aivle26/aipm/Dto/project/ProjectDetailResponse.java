package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectDetailResponse(
        Long projectId,
        String name,
        String description,
        String clientOrganization,
        String pmEmployeeNumber,
        String pmName,
        ProjectStatus status,
        int progressRate,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        List<String> acceptanceConditions,
        List<String> budgetContractConditions,
        List<String> securityPrivacyConditions,
        FinalizationReadiness finalizationReadiness,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record FinalizationReadiness(
            boolean ready,
            boolean projectInfoReady,
            boolean requirementsReady,
            boolean wbsReady,
            boolean scheduleReady,
            boolean teamMembersReady,
            boolean assignmentsReady,
            boolean costEstimateReady,
            List<String> missingItems
    ) {
    }
}
