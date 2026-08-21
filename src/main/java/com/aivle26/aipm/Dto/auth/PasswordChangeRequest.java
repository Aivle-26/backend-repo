package com.aivle26.aipm.Dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank
        String employeeNumber,

        @NotBlank
        String resetToken,

        @NotBlank
        @Size(min = 8, max = 100)
        String newPassword
) {
}
