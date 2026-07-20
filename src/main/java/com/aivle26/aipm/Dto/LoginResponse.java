package com.aivle26.aipm.Dto;

public record LoginResponse(
        boolean success,
        boolean verificationRequired,
        String message,
        long expiresIn
) {
}
