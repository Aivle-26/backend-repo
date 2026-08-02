package com.aivle26.aipm.Dto.project;

import java.util.List;

public record CostEstimateResponse(
        Long projectId,
        String currency,
        double totalEstimatedMm,
        CostSummary costSummary,
        Estimate estimate,
        List<String> unpricedItems,
        String warning,
        String llmStatus
) {
    public record CostSummary(
            long laborCost,
            long serverCost,
            long licenseCost,
            long aiApiCost,
            long baseCost
    ) {
    }

    public record Estimate(
            int contingencyRate,
            long contingencyAmount,
            long supplyAmount,
            long vat,
            long totalAmount
    ) {
    }
}
