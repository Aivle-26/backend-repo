package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordEmailCheckRequest(
        @NotBlank
        String employeeNumber,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code
) {
}
