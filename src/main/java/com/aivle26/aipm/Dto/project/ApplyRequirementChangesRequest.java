package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ApplyRequirementChangesRequest(
        @NotEmpty
        List<@Positive Long> candidateIds
) {
}
