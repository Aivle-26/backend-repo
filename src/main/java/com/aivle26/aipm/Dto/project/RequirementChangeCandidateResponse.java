package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.RequirementChangeReviewStatus;
import com.aivle26.aipm.Entity.project.RequirementChangeType;

import java.time.LocalDateTime;
import java.util.List;

public record RequirementChangeCandidateResponse(
        Long candidateId,
        Long existingRequirementId,
        RequirementChangeType changeType,
        RequirementChangeReviewStatus reviewStatus,
        String changeReason,
        RequirementChangeProposal existingRequirement,
        RequirementChangeProposal proposedRequirement,
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> evidences,
        boolean applied,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}
