package com.aivle26.aipm.Dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank
        String email,

        @NotBlank
        String password,

        @NotBlank
        String role
) {
}
