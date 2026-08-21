package com.aivle26.aipm.Dto.project;

public record ProjectProgressResponse(
        Long projectId,
        String employeeNumber,
        int progressRate,
        int totalTaskCount,
        int assignedTaskCount,
        int completedTaskCount,
        int delayedTaskCount,
        int totalEstimatedHours,
        int completedEstimatedHours
) {
}
