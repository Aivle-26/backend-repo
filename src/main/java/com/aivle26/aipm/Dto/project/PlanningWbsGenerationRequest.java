package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningWbsGenerationRequest(
        @JsonProperty("project_info")
        ProjectInfo projectInfo,

        @JsonProperty("requirement_candidates")
        List<RequirementData> requirementCandidates,

        List<String> methodology
) {
    public record ProjectInfo(
            @JsonProperty("project_name")
            String projectName,

            @JsonProperty("project_goal")
            String projectGoal,

            @JsonProperty("client_organization")
            String clientOrganization,

            @JsonProperty("period_start")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate periodStart,

            @JsonProperty("period_end")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate periodEnd,

            @JsonProperty("key_features")
            List<String> keyFeatures,

            @JsonProperty("required_artifacts")
            List<RequiredArtifact> requiredArtifacts,

            @JsonProperty("acceptance_conditions")
            List<String> acceptanceConditions,

            @JsonProperty("budget_contract_conditions")
            List<String> budgetContractConditions,

            @JsonProperty("security_privacy_conditions")
            List<String> securityPrivacyConditions
    ) {
    }

    public record RequiredArtifact(
            @JsonProperty("artifact_type")
            String artifactType,

            @JsonProperty("artifact_name")
            String artifactName,

            @JsonProperty("required_version")
            String requiredVersion
    ) {
    }

    public record RequirementData(
            @JsonProperty("requirement_id")
            Long requirementId,

            @JsonProperty("function_name")
            String functionName,

            @JsonProperty("requirement_text")
            String requirementText,

            String category,

            String priority,

            @JsonProperty("acceptance_criteria")
            String acceptanceCriteria,

            @JsonProperty("due_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            LocalDate dueDate,

            @JsonProperty("deliverable_name")
            String deliverableName,

            @JsonProperty("security_condition")
            String securityCondition,

            @JsonProperty("source_document")
            String sourceDocument,

            @JsonProperty("source_excerpt")
            String sourceExcerpt
    ) {
    }
}
