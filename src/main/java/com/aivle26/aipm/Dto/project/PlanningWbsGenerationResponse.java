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

        @JsonProperty("requirement_coverage")
        RequirementCoverage requirementCoverage,

        @JsonProperty("artifact_coverage")
        ArtifactCoverage artifactCoverage,

        List<String> warnings,

        @JsonProperty("generation_status")
        String generationStatus,

        @JsonProperty("llm_status")
        String llmStatus
) {
    public PlanningWbsGenerationResponse(
            String projectName,
            List<String> methodology,
            List<WbsItem> wbsItems,
            List<String> warnings,
            String generationStatus
    ) {
        this(projectName, methodology, wbsItems, null, null, warnings, generationStatus, null);
    }

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
            List<Long> mappedRequirementIds,

            @JsonProperty("related_artifacts")
            List<RelatedArtifact> relatedArtifacts,

            @JsonProperty("completion_criteria")
            List<String> completionCriteria,

            @JsonProperty("required_skills")
            List<String> requiredSkills
    ) {
        public WbsItem(
                Long wbsId,
                String wbsCode,
                Long parentWbsId,
                Integer level,
                Integer sortOrder,
                String itemType,
                String wbsName,
                String description,
                List<Long> mappedRequirementIds
        ) {
            this(wbsId, wbsCode, parentWbsId, level, sortOrder, itemType, wbsName,
                    description, mappedRequirementIds, List.of(), List.of(), List.of());
        }

        public WbsItem(
                Long wbsId,
                String wbsCode,
                Long parentWbsId,
                Integer level,
                Integer sortOrder,
                String itemType,
                String wbsName,
                String description,
                List<Long> mappedRequirementIds,
                List<RelatedArtifact> relatedArtifacts,
                List<String> completionCriteria
        ) {
            this(wbsId, wbsCode, parentWbsId, level, sortOrder, itemType, wbsName,
                    description, mappedRequirementIds, relatedArtifacts, completionCriteria, List.of());
        }
    }

    public record RelatedArtifact(
            @JsonProperty("artifact_type") String artifactType,
            @JsonProperty("artifact_name") String artifactName,
            @JsonProperty("required_version") String requiredVersion
    ) {
    }

    public record RequirementCoverage(
            @JsonProperty("total_requirements") int totalRequirements,
            @JsonProperty("mapped_requirements") int mappedRequirements,
            @JsonProperty("unmapped_requirement_ids") List<Long> unmappedRequirementIds,
            @JsonProperty("coverage_rate") double coverageRate
    ) {
    }

    public record ArtifactCoverage(
            @JsonProperty("total_required_artifacts") int totalRequiredArtifacts,
            @JsonProperty("mapped_artifacts") int mappedArtifacts,
            @JsonProperty("unmapped_artifact_types") List<String> unmappedArtifactTypes,
            @JsonProperty("coverage_rate") double coverageRate
    ) {
    }
}
