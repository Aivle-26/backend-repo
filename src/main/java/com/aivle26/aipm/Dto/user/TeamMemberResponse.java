package com.aivle26.aipm.Dto.user;

import java.util.List;

public record TeamMemberResponse(
        String employeeNumber,
        String name,
        String email,
        boolean capabilityRegistered,
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
