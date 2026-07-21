package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectSummaryResponse(
        Long projectId,
        String name,
        String description,
        String pmEmployeeNumber,
        ProjectStatus status,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
