package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateScrumRequestsRequest(
        @NotNull LocalDate weekStartDate,
        @NotEmpty @Size(max = 100) List<@jakarta.validation.constraints.NotBlank @Size(max = 50) String> recipientEmployeeNumbers,
        @Size(max = 4000) String message
) {
}
