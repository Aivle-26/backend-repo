package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.UserStatus;

public record LoginResponse(
        String employeeNumber,
        String name,
        String role,
        UserStatus status,
        String message
) {
}
