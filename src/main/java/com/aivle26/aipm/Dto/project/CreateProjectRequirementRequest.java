package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateProjectRequirementRequest(
        Long analysisResultId,

        @NotNull
        Long sourceDocumentId,

        @NotNull
        Long externalReferenceId,

        @NotNull
        RequirementType type,

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Size(max = 2000)
        String acceptanceCriteria,

        LocalDate dueDate,

        @Size(max = 255)
        String deliverableName,

        @Size(max = 1000)
        String securityCondition,

        @Size(max = 255)
        String sourceDocumentName,

        String sourceExcerpt,

        @NotNull
        RequirementPriority priority
) {
}
