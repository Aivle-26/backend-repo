package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskProgressRequest(
        @NotNull TaskProgressStatus status,
        @Min(0) @Max(100) int progressRate
) {
}
