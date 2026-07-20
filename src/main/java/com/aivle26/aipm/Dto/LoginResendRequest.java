package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginResendRequest(
        @NotBlank
        @Email
        String email
) {
}
