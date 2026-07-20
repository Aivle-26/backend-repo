package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.UserStatus;

public record UserResponse(
        String employeeNumber,
        String name,
        String email,
        String role,
        UserStatus status
) {
}
