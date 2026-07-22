package com.aivle26.aipm.Config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ai-server")
public class AiServerProperties {
    @NotBlank
    private String baseUrl = "http://localhost:8000";

    @NotBlank
    private String documentExtractPath = "/api/v1/planning/documents/extract";

    @Min(1)
    private int connectTimeoutSeconds = 5;

    @Min(1)
    private int responseTimeoutSeconds = 120;
}
