package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AnalyzeProjectRequirementsRequest(
        @NotEmpty
        List<@NotNull @Positive Long> documentIds,
        Boolean force
) {
    public boolean forceRequested() {
        return Boolean.TRUE.equals(force);
    }
}
