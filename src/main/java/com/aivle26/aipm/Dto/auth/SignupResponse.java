package com.aivle26.aipm.Dto.auth;

import com.aivle26.aipm.Entity.user.UserStatus;



public record SignupResponse(
        String employeeNumber,
        String name,
        String email,
        String role,
        UserStatus status
) {
}
