package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordEmailSendRequest(
        @NotBlank
        String employeeNumber,

        @NotBlank
        @Email
        String email
) {
}
