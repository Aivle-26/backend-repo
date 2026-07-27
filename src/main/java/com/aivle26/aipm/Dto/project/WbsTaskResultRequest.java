package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WbsTaskResultRequest(
        @NotBlank
        @Size(max = 100)
        String externalTaskId,

        @Size(max = 100)
        String parentExternalTaskId,

        @NotBlank
        @Size(max = 50)
        String taskCode,

        @NotBlank
        @Size(max = 200)
        String taskName,

        @NotBlank
        @Size(max = 2000)
        String description,

        @NotBlank
        String phase,

        @NotNull
        List<@NotBlank String> requiredSkills,

        @NotBlank
        String difficulty,

        @Min(1)
        int estimatedHours,

        @Min(0)
        int orderIndex,

        @NotNull
        List<@NotNull Long> requirementIds
) {
}
