package com.aivle26.aipm.Dto.auth;

public record LoginResponse(
        boolean success,
        boolean verificationRequired,
        String message,
        long expiresIn
) {
}
