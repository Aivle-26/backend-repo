package com.aivle26.aipm.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SaveScheduleResultRequest(
        @NotBlank
        @Size(max = 100)
        String agentExecutionId,

        @NotBlank
        @Size(max = 100)
        String agentVersion,

        @NotNull
        LocalDate projectStartDate,

        @NotNull
        LocalDate targetEndDate,

        @NotNull
        @Valid
        List<ScheduleResultRequest> schedules
) {
}
