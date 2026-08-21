package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UiMockupAssessmentAiResponse(
        @JsonProperty("project_id") Long projectId,
        UiMockupAssessmentResponse.Decision decision,
        String reason,
        @JsonProperty("evidence_requirement_ids") List<Long> evidenceRequirementIds,
        @JsonProperty("candidate_screens") List<String> candidateScreens
) {
}
