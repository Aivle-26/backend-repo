package com.aivle26.aipm.Config.ai;

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

    @NotBlank
    private String readjustPath = "/api/v1/planning/documents/readjust";

    @NotBlank
    private String wbsPath = "/api/v1/planning/wbs/generate";

    @NotBlank
    private String schedulePath = "/api/v1/planning/schedules/recommend";

    @NotBlank
    private String resourcePath = "/api/v1/planning/resources/recommend";

    @NotBlank
    private String organizationChartPath =
            "/api/v1/planning/resources/organization-chart/generate";

    @NotBlank
    private String costPath = "/api/v1/planning/costs/estimate";

    @NotBlank
    private String effortEstimatePath = "/api/v1/planning/costs/effort-estimate";

    @NotBlank
    private String ragQueryPath = "/api/v1/reports/deliverables/rag/query";

    @NotBlank
    private String weeklyScrumSummarizePath = "/api/v1/reports/weekly-scrum/summarize";

    @NotBlank
    private String weeklyScrumReviewPath = "/api/v1/reports/weekly-scrum/review";

    @NotBlank
    private String weeklyScrumRecommendPath = "/api/v1/reports/weekly-scrum/recommend-next-actions";

    @NotBlank
    private String weeklyScrumFinalizePath = "/api/v1/reports/weekly-scrum/finalize";

    @NotBlank
    private String scheduleAgentVersion = "schedule-recommend-v1";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(120);
}
