package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementType;

import java.time.LocalDate;
import java.util.List;

public record RequirementChangeProposal(
        Long requirementId,
        Long sourceDocumentId,
        String functionName,
        String requirementText,
        RequirementType category,
        RequirementPriority priority,
        String acceptanceCriteria,
        LocalDate dueDate,
        String deliverableName,
        String securityCondition,
        String sourceDocument,
        String sourceExcerpt,
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> evidences
) {
}
