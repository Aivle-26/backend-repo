package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SaveFinalScheduleRequest(
        @NotNull LocalDate projectStartDate,
        @NotNull LocalDate targetEndDate,
        @NotNull @Size(min = 1) @Valid List<@NotNull @Valid ScheduleItem> schedules
) {
    public record ScheduleItem(
            @NotBlank @Size(max = 100) String externalScheduleId,
            @NotNull Long wbsId,
            @NotNull @Valid DateRange expected,
            @NotNull @Valid DateRange recommended,
            @NotNull @Valid DateRange conservative,
            @NotNull List<@NotNull Long> predecessorWbsIds,
            boolean milestone,
            @Min(0) int bufferDays
    ) {
    }

    public record DateRange(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {
    }
}
