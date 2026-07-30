package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningRequirementReadjustResponse(
        @JsonProperty("change_candidates")
        List<ChangeCandidate> changeCandidates,

        List<PlanningDocumentExtractResponse.DocumentResult> documents,

        @JsonProperty("llm_status")
        String llmStatus
) {
    public record ChangeCandidate(
            @JsonProperty("candidate_id")
            String candidateId,

            @JsonProperty("existing_requirement_id")
            Long existingRequirementId,

            @JsonProperty("change_type")
            String changeType,

            @JsonProperty("change_reason")
            String changeReason,

            @JsonProperty("existing_requirement")
            ExistingRequirement existingRequirement,

            @JsonProperty("proposed_requirement")
            PlanningDocumentExtractResponse.RequirementCandidate proposedRequirement,

            List<PlanningDocumentExtractResponse.RequirementEvidence> evidences,

            @JsonProperty("review_status")
            String reviewStatus
    ) {
    }

    public record ExistingRequirement(
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
            String sourceExcerpt,

            List<PlanningDocumentExtractResponse.RequirementEvidence> evidences
    ) {
    }
}
