package com.aivle26.aipm.Dto.auth;

public record PasswordEmailCheckResponse(
        String resetToken,
        String message
) {
}
