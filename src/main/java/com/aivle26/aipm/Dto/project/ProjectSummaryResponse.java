package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.ProjectStatus;



import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectSummaryResponse(
        Long projectId,
        String name,
        String description,
        String clientOrganization,
        String pmEmployeeNumber,
        ProjectStatus status,
        int progressRate,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
