package com.aivle26.aipm.Dto;

public record SaveDocumentAnalysisResultResponse(
        Long analysisResultId,
        Long projectId,
        String agentExecutionId,
        int requirementsCount
) {
}
