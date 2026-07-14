package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveDocumentAnalysisResultRequest(
        @NotBlank
        @Size(max = 100)
        String agentExecutionId,

        @NotBlank
        @Size(max = 100)
        String agentVersion,

        @NotBlank
        @Size(max = 500)
        String projectGoal,

        @NotBlank
        @Size(max = 1000)
        String scope,

        @NotNull
        @Valid
        List<DocumentAnalysisRequirementRequest> requirements,

        JsonNode deliverables,
        JsonNode milestones,
        JsonNode technologyStacks,
        JsonNode constraints,
        JsonNode risks
) {
}
