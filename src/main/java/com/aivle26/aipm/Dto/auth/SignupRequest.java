package com.aivle26.aipm.Dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(max = 50)
        String employeeNumber,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must include letters and numbers")
        String password,

        @NotBlank
        @Pattern(regexp = "PM|STAFF", flags = Pattern.Flag.CASE_INSENSITIVE)
        String role
) {
}
