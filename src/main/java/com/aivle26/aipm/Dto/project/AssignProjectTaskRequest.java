package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AssignProjectTaskRequest(
        @NotBlank @Size(max = 50) String employeeNumber,
        LocalDate dueDate
) {
}
