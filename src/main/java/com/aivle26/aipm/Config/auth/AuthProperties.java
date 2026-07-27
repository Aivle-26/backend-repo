package com.aivle26.aipm.Config.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    @Min(1)
    private long accessTokenExpirationMinutes = 30;

    @Min(1)
    private long absoluteLoginExpirationHours = 8;

    @Min(1)
    private long inactivityTimeoutMinutes = 30;

    @NotBlank
    @Size(min = 32)
    private String jwtSecret;
}
