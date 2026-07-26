package com.aivle26.aipm.Dto.auth;

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
