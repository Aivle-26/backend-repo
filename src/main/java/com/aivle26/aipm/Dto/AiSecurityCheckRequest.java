package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 서버(FastAPI)의 ArtifactSecurityRequest.
 * 엔드포인트: POST {base-url}/api/v1/risk/artifact-security
 */
public record AiSecurityCheckRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("artifact_name") String artifactName,
        @JsonProperty("artifact_type") String artifactType,
        @JsonProperty("text_content") String textContent
) {
}
