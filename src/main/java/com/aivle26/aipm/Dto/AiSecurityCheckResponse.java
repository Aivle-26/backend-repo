package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** AI 서버(FastAPI)의 ArtifactSecurityResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSecurityCheckResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("artifact_name") String artifactName,
        @JsonProperty("security_risk_score") int securityRiskScore,
        @JsonProperty("security_risk_level") String securityRiskLevel,
        @JsonProperty("registration_allowed") boolean registrationAllowed,
        @JsonProperty("detections") List<AiDetection> detections,
        @JsonProperty("masked_content") String maskedContent,
        @JsonProperty("recommendations") List<String> recommendations
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiDetection(
            @JsonProperty("detection_type") String detectionType,
            @JsonProperty("count") int count,
            @JsonProperty("description") String description
    ) {
    }
}
