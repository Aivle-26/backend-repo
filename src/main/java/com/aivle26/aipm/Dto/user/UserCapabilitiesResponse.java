package com.aivle26.aipm.Dto.user;

import java.util.List;

public record UserCapabilitiesResponse(
        String employeeNumber,
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
