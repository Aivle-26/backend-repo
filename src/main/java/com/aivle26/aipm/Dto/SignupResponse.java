package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.UserStatus;

public record SignupResponse(
        String employeeNumber,
        String name,
        String email,
        String role,
        UserStatus status
) {
}
