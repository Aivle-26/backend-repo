package com.aivle26.aipm.Dto;

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
