package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버(FastAPI)의 ImpactAssessmentResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImpactAnalysisResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("requirement_id") Long requirementId,
        @JsonProperty("impact_score") int impactScore,
        @JsonProperty("impact_level") String impactLevel,
        @JsonProperty("schedule_impact_score") int scheduleImpactScore,
        @JsonProperty("scope_impact_score") int scopeImpactScore,
        @JsonProperty("resource_impact_score") int resourceImpactScore,
        @JsonProperty("technical_impact_score") int technicalImpactScore,
        @JsonProperty("risk_factors") List<String> riskFactors,
        @JsonProperty("recommended_actions") List<String> recommendedActions
) {
}
