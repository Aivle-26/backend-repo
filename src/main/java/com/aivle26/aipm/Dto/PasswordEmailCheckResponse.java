package com.aivle26.aipm.Dto;

public record PasswordEmailCheckResponse(
        String resetToken,
        String message
) {
}
