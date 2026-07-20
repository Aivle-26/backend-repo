package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
