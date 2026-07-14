package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record ScheduleResultRequest(
        @NotBlank
        String externalScheduleId,

        @NotNull
        Long wbsId,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @Min(1)
        int estimatedDays,

        @NotNull
        List<@NotNull Long> predecessorWbsIds,

        boolean milestone,

        @Min(0)
        int bufferDays
) {
}
