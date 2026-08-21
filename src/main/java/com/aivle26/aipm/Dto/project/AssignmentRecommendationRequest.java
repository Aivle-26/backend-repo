package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignmentRecommendationRequest(
        @Size(max = 500) @Valid List<@Valid Candidate> candidates
) {
    public record Candidate(
            @NotBlank @Size(max = 50) String employeeNumber,
            @DecimalMin("0.0") @DecimalMax("168.0") Double availableHoursPerWeek
    ) {
    }
}
