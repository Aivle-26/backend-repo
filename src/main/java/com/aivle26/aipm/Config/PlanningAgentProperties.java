package com.aivle26.aipm.Config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "agent.planning")
public class PlanningAgentProperties {
    @NotBlank
    private String baseUrl = "http://localhost:8000";

    @NotBlank
    private String extractPath = "/api/v1/planning/documents/extract";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(120);
}
