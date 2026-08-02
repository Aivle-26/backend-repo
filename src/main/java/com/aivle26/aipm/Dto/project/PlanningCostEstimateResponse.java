package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlanningCostEstimateResponse(
        @JsonProperty("project_id") Long projectId,
        String currency,
        @JsonProperty("total_estimated_mm") double totalEstimatedMm,
        @JsonProperty("cost_summary") CostSummary costSummary,
        Estimate estimate,
        @JsonProperty("unpriced_items") List<String> unpricedItems,
        String warning,
        @JsonProperty("llm_status") String llmStatus
) {
    public record CostSummary(
            @JsonProperty("labor_cost") long laborCost,
            @JsonProperty("server_cost") long serverCost,
            @JsonProperty("license_cost") long licenseCost,
            @JsonProperty("ai_api_cost") long aiApiCost,
            @JsonProperty("base_cost") long baseCost
    ) {
    }

    public record Estimate(
            @JsonProperty("contingency_rate") int contingencyRate,
            @JsonProperty("contingency_amount") long contingencyAmount,
            @JsonProperty("supply_amount") long supplyAmount,
            long vat,
            @JsonProperty("total_amount") long totalAmount
    ) {
    }
}
