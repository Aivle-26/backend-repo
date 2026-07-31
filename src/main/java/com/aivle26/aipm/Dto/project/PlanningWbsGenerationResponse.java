package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanningWbsGenerationResponse(
        @JsonProperty("project_name")
        String projectName,

        List<String> methodology,

        @JsonProperty("wbs_items")
        List<WbsItem> wbsItems,

        List<String> warnings,

        @JsonProperty("generation_status")
        String generationStatus
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WbsItem(
            @JsonProperty("wbs_id")
            Long wbsId,

            @JsonProperty("wbs_code")
            String wbsCode,

            @JsonProperty("parent_wbs_id")
            Long parentWbsId,

            Integer level,

            @JsonProperty("sort_order")
            Integer sortOrder,

            @JsonProperty("item_type")
            String itemType,

            @JsonProperty("wbs_name")
            String wbsName,

            String description,

            @JsonProperty("mapped_requirement_ids")
            List<Long> mappedRequirementIds
    ) {
    }
}
