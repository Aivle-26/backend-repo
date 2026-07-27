package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementType;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProjectRequirementRequest(
        Long analysisResultId,
        Long sourceDocumentId,

        Long externalReferenceId,

        RequirementType type,

        @Size(max = 200)
        String title,

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
        RequirementPriority priority
) {
}
