package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.TaskProgressStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskAssignmentResponse(
        Long assignmentId,
        Long projectId,
        Long wbsId,
        String taskCode,
        String taskName,
        String description,
        String employeeNumber,
        TaskProgressStatus status,
        int progressRate,
        LocalDate startDate,
        LocalDate dueDate,
        int estimatedHours,
        double assignedHours,
        String assignedBy,
        boolean milestone,
        int bufferDays,
        boolean overdue,
        LocalDateTime assignedAt,
        LocalDateTime updatedAt
) {
}
