package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningDocumentExtractResponse(
        @JsonProperty("project_info")
        ProjectInfo projectInfo,

        @JsonProperty("requirement_candidates")
        List<RequirementCandidate> requirementCandidates,

        List<DocumentResult> documents,

        @JsonProperty("llm_status")
        String llmStatus
) {
    public record ProjectInfo(
            @JsonProperty("project_name")
            String projectName,

            @JsonProperty("project_goal")
            String projectGoal,

            @JsonProperty("client_organization")
            String clientOrganization,

            @JsonProperty("period_start")
            LocalDate periodStart,

            @JsonProperty("period_end")
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

    public record RequirementCandidate(
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

    public record DocumentResult(
            @JsonProperty("file_name")
            String fileName,

            @JsonProperty("file_type")
            String fileType,

            @JsonProperty("character_count")
            Long characterCount,

            @JsonProperty("processing_mode")
            String processingMode
    ) {
    }
}
