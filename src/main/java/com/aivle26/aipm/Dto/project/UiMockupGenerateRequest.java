package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UiMockupGenerateRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("project_title") String projectTitle,
        @JsonProperty("project_description") String projectDescription,
        @JsonProperty("confirmed_requirements") List<ConfirmedRequirement> confirmedRequirements
) {
    public record ConfirmedRequirement(
            @JsonProperty("requirement_id") Long requirementId,
            String title,
            String description,
            String category,
            String priority
    ) {
    }
}
