package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SaveFinalTaskAssignmentsRequest(
        @NotEmpty @Size(max = 1000) @Valid List<@Valid Assignment> assignments
) {
    public record Assignment(
            @NotNull Long wbsId,
            @NotBlank @Size(max = 50) String employeeNumber,
            @DecimalMin(value = "0.0", inclusive = false)
            @DecimalMax("100000.0")
            Double assignedHours,
            LocalDate dueDate
    ) {
    }
}
