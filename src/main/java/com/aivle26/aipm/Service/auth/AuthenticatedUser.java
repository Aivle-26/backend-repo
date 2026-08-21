package com.aivle26.aipm.Service.auth;

public record AuthenticatedUser(
        String employeeNumber,
        String role
) {
}
