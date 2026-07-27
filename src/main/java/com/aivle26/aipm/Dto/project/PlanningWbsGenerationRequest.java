package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningWbsGenerationRequest(
        List<RequirementData> requirements
) {
    public record RequirementData(
            @JsonProperty("requirement_id")
            Long requirementId,

            String type,

            String title,

            String description,

            @JsonProperty("acceptance_criteria")
            String acceptanceCriteria,

            @JsonProperty("due_date")
            LocalDate dueDate,

            @JsonProperty("deliverable_name")
            String deliverableName,

            @JsonProperty("security_condition")
            String securityCondition,

            String priority
    ) {
    }
}
