package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank
        String employeeNumber,

        @NotBlank
        String password
) {
}
