package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record UiMockupGenerateResponse(
        @JsonProperty("project_id") Long projectId,
        JsonNode mockup,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("image_base64") String imageBase64,
        int width,
        int height
) {
}
