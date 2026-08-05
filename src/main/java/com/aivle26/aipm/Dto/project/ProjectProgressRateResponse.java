package com.aivle26.aipm.Dto.project;

import java.time.LocalDateTime;

public record ProjectProgressRateResponse(
        Long projectId,
        int progressRate,
        LocalDateTime updatedAt
) {
}
