package com.aivle26.aipm.Dto.auth;

public record LoginVerifyResponse(
        boolean success,
        String message,
        String employeeNumber,
        String name,
        String role,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresAt,
        long absoluteExpiresAt,
        long lastActivityAt,
        long serverTime,
        long inactivityTimeoutMinutes
) {
}
