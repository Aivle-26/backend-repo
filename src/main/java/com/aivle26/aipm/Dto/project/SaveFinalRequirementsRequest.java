package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SaveFinalRequirementsRequest(
        @NotNull
        @Valid
        List<@NotNull @Valid RequirementItem> requirements
) {
    public record RequirementItem(
            @Positive
            Long requirementId,

            Long analysisResultId,

            @NotNull
            Long sourceDocumentId,

            @NotNull
            @Positive
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
}
