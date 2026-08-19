package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.WbsGenerationStatus;

import java.time.LocalDateTime;

public record WbsGenerationStartResponse(
        String generationId,
        Long projectId,
        WbsGenerationStatus status,
        boolean reused,
        LocalDateTime requestedAt
) {
}
