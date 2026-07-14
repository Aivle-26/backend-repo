package com.aivle26.aipm.Dto;

public record SaveWbsResultResponse(
        Long wbsResultId,
        Long projectId,
        String agentExecutionId,
        int taskCount
) {
}
