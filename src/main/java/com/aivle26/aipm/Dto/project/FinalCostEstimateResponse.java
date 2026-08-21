package com.aivle26.aipm.Dto.project;

import java.time.LocalDateTime;
import java.util.List;

public record FinalCostEstimateResponse(
        Long costEstimateId,
        Long projectId,
        boolean confirmed,
        List<CostEstimateRequest.WbsEffort> wbsEfforts,
        long averageMonthlyUnitPrice,
        int operationMonths,
        CostEstimateRequest.ServiceScale serviceScale,
        boolean usesAiApi,
        int paidLicenseUserCount,
        boolean includeVat,
        String currency,
        double totalEstimatedMm,
        CostEstimateResponse.CostSummary costSummary,
        CostEstimateResponse.Estimate estimate,
        List<String> unpricedItems,
        String warning,
        String llmStatus,
        LocalDateTime updatedAt
) {
}
