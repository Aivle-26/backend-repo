package com.aivle26.aipm.Dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginResendRequest(
        @NotBlank
        @Email
        String email
) {
}
