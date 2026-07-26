package com.aivle26.aipm.Dto.project;


public record SaveWbsResultResponse(
        Long wbsResultId,
        Long projectId,
        String agentExecutionId,
        int taskCount
) {
}
