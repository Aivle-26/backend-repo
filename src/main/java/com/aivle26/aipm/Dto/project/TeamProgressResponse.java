package com.aivle26.aipm.Dto.project;

import java.util.List;

public record TeamProgressResponse(
        Long projectId,
        List<MemberProgress> members
) {
    public record MemberProgress(
            String employeeNumber,
            String name,
            int progressRate,
            int totalTaskCount,
            int completedTaskCount,
            int delayedTaskCount,
            int totalEstimatedHours
    ) {
    }
}
