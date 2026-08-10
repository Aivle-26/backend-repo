package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class EditableCostEstimate {

    private EditableCostEstimate() {
    }

    public record Request(
            @NotEmpty @Size(max = 200) @Valid List<@NotNull @Valid PersonnelInput> personnel,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal overheadRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal technicalFeeRate,
            @NotNull @Min(0) @Max(100_000_000_000L) Long directExpense,
            @Size(max = 100) @Valid List<@NotNull @Valid ExpenseItemInput> expenseItems,
            @NotNull @Min(0) @Max(100_000_000_000L) Long discountAmount,
            Boolean includeVat,
            @Size(max = 1000) String note
    ) {
        public record PersonnelInput(
                @NotBlank @Size(max = 50) String employeeNumber,
                @NotBlank @Size(max = 100) String kosaJobCategory,
                @NotBlank @Size(max = 100) String detailedJob,
                @NotNull @Min(1) @Max(100) Integer headcount,
                @NotNull @DecimalMin(value = "0.01") @DecimalMax("120") BigDecimal durationMonths,
                @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal utilizationRate,
                @NotNull @Min(0) @Max(1_000_000_000L) Long proposedMonthlyRate
        ) {
        }

        public record ExpenseItemInput(
                @NotBlank @Size(max = 100) String name,
                @NotNull @DecimalMin(value = "0.01") @DecimalMax("1000000") BigDecimal quantity,
                @NotBlank @Size(max = 30) String unit,
                @NotNull @Min(0) @Max(100_000_000_000L) Long unitPrice,
                Boolean included
        ) {
        }
    }

    public record Response(
            Long costEstimateId,
            Long projectId,
            boolean confirmed,
            int kosaRateYear,
            String currency,
            boolean hasUtilizationWarning,
            List<UtilizationWarning> utilizationWarnings,
            List<Personnel> personnel,
            List<ExpenseItem> expenseItems,
            BigDecimal totalMm,
            long directLaborCost,
            BigDecimal overheadRate,
            long overheadAmount,
            BigDecimal technicalFeeRate,
            long technicalFeeAmount,
            long directExpense,
            long developmentCost,
            long expenseItemTotal,
            long discountAmount,
            long supplyAmount,
            boolean includeVat,
            long vat,
            long totalAmount,
            String note,
            LocalDateTime updatedAt
    ) {
        public record UtilizationWarning(
                String employeeNumber,
                String employeeName,
                BigDecimal totalUtilizationRate,
                BigDecimal excessUtilizationRate,
                List<String> detailedJobs,
                String message
        ) {
        }

        public record Personnel(
                String employeeNumber,
                String employeeName,
                String kosaJobCategory,
                String detailedJob,
                int headcount,
                BigDecimal durationMonths,
                BigDecimal utilizationRate,
                BigDecimal calculatedMm,
                long standardMonthlyRate,
                long proposedMonthlyRate,
                long amount
        ) {
        }

        public record ExpenseItem(
                String name,
                BigDecimal quantity,
                String unit,
                long unitPrice,
                boolean included,
                long amount
        ) {
        }
    }
}
