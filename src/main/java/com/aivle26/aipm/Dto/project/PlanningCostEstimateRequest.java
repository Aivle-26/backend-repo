package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlanningCostEstimateRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("project_name") String projectName,
        @JsonProperty("wbs_efforts") List<WbsEffort> wbsEfforts,
        @JsonProperty("average_monthly_unit_price") long averageMonthlyUnitPrice,
        @JsonProperty("operation_months") int operationMonths,
        @JsonProperty("service_scale") CostEstimateRequest.ServiceScale serviceScale,
        @JsonProperty("uses_ai_api") boolean usesAiApi,
        @JsonProperty("paid_license_user_count") int paidLicenseUserCount,
        @JsonProperty("include_vat") boolean includeVat
) {
    public record WbsEffort(
            @JsonProperty("wbs_id") Long wbsId,
            @JsonProperty("wbs_name") String wbsName,
            String description,
            @JsonProperty("estimated_mm") double estimatedMm
    ) {
    }
}
