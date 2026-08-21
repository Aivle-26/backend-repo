package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.WeeklyScrumSubmission;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WeeklyScrumSubmissionResponse(
        Long id,
        Long projectId,
        String employeeNumber,
        LocalDate weekStartDate,
        String completedWork,
        String plannedWork,
        String blockers,
        SaveWeeklyScrumRequest.Details details,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WeeklyScrumSubmissionResponse from(
            WeeklyScrumSubmission submission,
            SaveWeeklyScrumRequest.Details details
    ) {
        return new WeeklyScrumSubmissionResponse(
                submission.getId(),
                submission.getProject().getId(),
                submission.getEmployeeNumber(),
                submission.getWeekStartDate(),
                submission.getCompletedWork(),
                submission.getPlannedWork(),
                submission.getBlockers(),
                details,
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
