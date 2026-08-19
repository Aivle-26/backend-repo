package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.WbsGenerationStatus;

import java.time.LocalDateTime;

public record WbsGenerationStatusResponse(
        String generationId,
        Long projectId,
        WbsGenerationStatus status,
        Long wbsResultId,
        String errorCode,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
