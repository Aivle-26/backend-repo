package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentAnalysisRequirementRequest(
        @NotNull
        Long externalReferenceId,

        @NotBlank
        String type,

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @NotBlank
        String priority,

        @NotNull
        Long sourceDocumentId
) {
}
