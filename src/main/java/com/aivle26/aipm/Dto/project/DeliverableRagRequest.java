package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DeliverableRagRequest(
        @JsonProperty("project_id") Long projectId,
        String question,
        @JsonProperty("deliverable_documents") List<Document> deliverableDocuments,
        @JsonProperty("enable_llm") boolean enableLlm
) {
    public record Document(
            @JsonProperty("deliverable_id") String deliverableId,
            @JsonProperty("document_id") String documentId,
            @JsonProperty("document_name") String documentName,
            String text,
            Integer page,
            @JsonProperty("requirement_id") Long requirementId,
            @JsonProperty("wbs_id") Long wbsId,
            @JsonProperty("review_status") String reviewStatus
    ) {
    }
}
