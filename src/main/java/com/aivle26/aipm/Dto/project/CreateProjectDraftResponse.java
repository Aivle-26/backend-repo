package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.ProjectStatus;



import java.time.LocalDate;

public record CreateProjectDraftResponse(
        Long projectId,
        String name,
        String clientOrganization,
        String pmEmployeeNumber,
        ProjectStatus status,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate
) {
}
