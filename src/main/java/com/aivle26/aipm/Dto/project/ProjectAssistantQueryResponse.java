package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectAssistantQueryResponse(
        @JsonProperty("project_id") Long projectId,
        String answer,
        List<Source> sources,
        @JsonProperty("generated_at") LocalDateTime generatedAt,
        @JsonProperty("llm_status") String llmStatus
) {
    public record Source(
            @JsonProperty("deliverable_id") String deliverableId,
            @JsonProperty("document_id") String documentId,
            @JsonProperty("document_name") String documentName,
            Integer page,
            String excerpt,
            @JsonProperty("requirement_id") Long requirementId,
            @JsonProperty("wbs_id") Long wbsId,
            @JsonProperty("review_status") String reviewStatus
    ) {
    }
}
