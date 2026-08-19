package com.aivle26.aipm.Service.project;

public record WbsGenerationRequestedEvent(
        String generationId,
        Long projectId
) {
}
