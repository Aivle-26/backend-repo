package com.aivle26.aipm.Dto.project;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectMemberResponse(
        Long projectMemberId,
        Long projectId,
        String employeeNumber,
        String name,
        String email,
        double availableHoursPerWeek,
        String selectedBy,
        LocalDateTime joinedAt,
        List<String> roles,
        List<Skill> skills
) {
    public record Skill(
            String skillCode,
            int proficiencyLevel,
            int experienceMonths
    ) {
    }
}
