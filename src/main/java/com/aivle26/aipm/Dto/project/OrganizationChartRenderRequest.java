package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizationChartRenderRequest(
        @JsonProperty("planning_request") PlanningResourceRecommendRequest planningRequest,
        OrganizationChartGenerateResponse.OrganizationView organization
) {
}
