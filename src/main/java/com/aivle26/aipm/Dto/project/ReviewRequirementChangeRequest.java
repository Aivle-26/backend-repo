package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.RequirementChangeReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewRequirementChangeRequest(
        @NotNull
        RequirementChangeReviewStatus reviewStatus,

        RequirementChangeProposal proposedRequirement
) {
}
