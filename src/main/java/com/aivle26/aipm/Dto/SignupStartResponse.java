package com.aivle26.aipm.Dto;

public record SignupStartResponse(
        boolean success,
        boolean verificationRequired,
        String message,
        int expiresIn
) {
}
