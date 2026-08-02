package com.aivle26.aipm.Entity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class UserSkill {

    @Column(name = "skill_code", nullable = false, length = 100)
    private String skillCode;

    @Column(name = "proficiency_level", nullable = false)
    private int proficiencyLevel;

    @Column(name = "experience_months", nullable = false)
    private int experienceMonths;
}
