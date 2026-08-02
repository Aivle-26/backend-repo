package com.aivle26.aipm.Dto.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveUserCapabilitiesRequest(
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 100) String> roles,
        @NotNull @Size(min = 1, max = 100) @Valid List<@NotNull @Valid Skill> skills
) {
    public record Skill(
            @JsonAlias("skill_code")
            @NotBlank @Size(max = 100) String skillCode,
            @JsonAlias("proficiency_level")
            @Max(5) @jakarta.validation.constraints.Min(1) int proficiencyLevel,
            @JsonAlias("experience_months")
            @PositiveOrZero int experienceMonths
    ) {
    }
}
