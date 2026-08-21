package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

public record SaveWeeklyScrumRequest(
        @NotBlank @Size(max = 4000) String completedWork,
        @NotBlank @Size(max = 4000) String plannedWork,
        @Size(max = 4000) String blockers,
        @Valid Details details
) {
    public SaveWeeklyScrumRequest(String completedWork, String plannedWork, String blockers) {
        this(completedWork, plannedWork, blockers, null);
    }

    public record Details(
            List<@Size(max = 500) String> weeklyGoals,
            List<@Valid ScrumItem> completedTasks,
            List<@Valid ScrumItem> inProgressTasks,
            List<@Valid ScrumItem> delayedTasks,
            List<@Valid ScrumItem> issues,
            List<@Valid ScrumItem> reportedRisks,
            List<@Valid ScrumItem> nextWeekTasks,
            List<@Valid ScrumItem> requests
    ) {
    }

    public record ScrumItem(
            @Size(max = 100) String itemId,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 4000) String description,
            @Size(max = 30) String taskType,
            @Size(max = 50) String ownerId,
            @Size(max = 100) String owner,
            LocalDate dueDate,
            @Size(max = 30) String status,
            @Size(max = 4000) String evidenceText,
            @Size(max = 2000) String doneCondition,
            @DecimalMin("0.0") Double estimatedHours,
            @Min(0) Integer carryoverCount,
            List<@Size(max = 100) String> dependencyIds,
            List<@Size(max = 100) String> relatedTaskIds,
            Boolean integrationRequired,
            @Size(max = 30) String sourceType,
            @Size(max = 100) String sourceReferenceId,
            @Size(max = 100) String requirementId,
            @Size(max = 100) String wbsId,
            @Size(max = 100) String deliverableId
    ) {
    }
}
