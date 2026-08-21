package com.aivle26.aipm.client.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;

public record AiServerJsonResponse(
        HttpStatusCode status,
        JsonNode body
) {
}
