package com.aivle26.aipm.client.ai;

import com.fasterxml.jackson.databind.JsonNode;

public interface WeeklyScrumAiClient {
    AiCallResult summarize(JsonNode request);

    AiCallResult review(JsonNode request);

    AiCallResult recommendNextActions(JsonNode request);

    AiCallResult finalizeReport(JsonNode request);

    record AiCallResult(JsonNode body, String requestId) {
    }
}
