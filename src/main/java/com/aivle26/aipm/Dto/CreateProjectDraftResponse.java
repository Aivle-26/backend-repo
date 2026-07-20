package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.ProjectStatus;

import java.time.LocalDate;

public record CreateProjectDraftResponse(
        Long projectId,
        String name,
        String pmEmployeeNumber,
        ProjectStatus status,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate
) {
}
