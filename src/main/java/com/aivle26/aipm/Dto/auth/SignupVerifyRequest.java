package com.aivle26.aipm.Dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupVerifyRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String verificationCode
) {
}
