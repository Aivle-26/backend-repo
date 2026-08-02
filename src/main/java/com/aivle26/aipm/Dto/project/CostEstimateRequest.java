package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CostEstimateRequest(
        @NotNull @Size(min = 1, max = 200) @Valid List<@NotNull @Valid WbsEffort> wbsEfforts,
        @NotNull @Min(1) @Max(1_000_000_000) Long averageMonthlyUnitPrice,
        @NotNull @Min(1) @Max(120) Integer operationMonths,
        @NotNull ServiceScale serviceScale,
        Boolean usesAiApi,
        @Min(0) @Max(10_000) Integer paidLicenseUserCount,
        Boolean includeVat
) {
    public enum ServiceScale {
        SMALL,
        MEDIUM,
        LARGE
    }

    public record WbsEffort(
            @NotNull @Positive Long wbsId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false)
            @DecimalMax("10000.0") Double estimatedMm
    ) {
    }
}
