package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveWeeklyScrumRequest(
        @NotBlank @Size(max = 4000) String completedWork,
        @NotBlank @Size(max = 4000) String plannedWork,
        @Size(max = 4000) String blockers
) {
}
