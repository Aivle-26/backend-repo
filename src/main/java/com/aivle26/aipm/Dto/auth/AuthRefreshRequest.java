package com.aivle26.aipm.Dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
