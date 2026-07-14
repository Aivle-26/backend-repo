package com.aivle26.aipm.Dto;

public record SaveScheduleResultResponse(
        Long scheduleResultId,
        Long projectId,
        String agentExecutionId,
        int scheduleCount
) {
}
