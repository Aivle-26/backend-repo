package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateProjectDraftRequest(
        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        @Size(max = 200)
        String clientOrganization,

        @NotBlank
        @Size(max = 50)
        String pmEmployeeNumber,

        @NotNull
        LocalDate plannedStartDate,

        @NotNull
        LocalDate plannedEndDate
) {
}
