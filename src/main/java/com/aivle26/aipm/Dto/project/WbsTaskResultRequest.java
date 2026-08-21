package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WbsTaskResultRequest(
        @JsonAlias("external_task_id")
        @NotBlank
        @Size(max = 100)
        String externalTaskId,

        @JsonAlias("parent_external_task_id")
        @Size(max = 100)
        String parentExternalTaskId,

        @JsonAlias("task_code")
        @NotBlank
        @Size(max = 50)
        String taskCode,

        @JsonAlias("task_name")
        @NotBlank
        @Size(max = 200)
        String taskName,

        @NotBlank
        @Size(max = 2000)
        String description,

        @NotBlank
        String phase,

        @JsonAlias("required_skills")
        @NotNull
        List<@NotBlank String> requiredSkills,

        @NotBlank
        String difficulty,

        @JsonAlias("estimated_hours")
        @Min(1)
        int estimatedHours,

        @JsonAlias("order_index")
        @Min(0)
        int orderIndex,

        @JsonAlias("requirement_ids")
        @NotNull
        List<@NotNull Long> requirementIds,

        @JsonAlias("related_artifacts")
        List<RelatedArtifact> relatedArtifacts,

        @JsonAlias("completion_criteria")
        List<String> completionCriteria
) {
    public WbsTaskResultRequest(
            String externalTaskId, String parentExternalTaskId, String taskCode,
            String taskName, String description, String phase, List<String> requiredSkills,
            String difficulty, int estimatedHours, int orderIndex, List<Long> requirementIds
    ) {
        this(externalTaskId, parentExternalTaskId, taskCode, taskName, description, phase,
                requiredSkills, difficulty, estimatedHours, orderIndex, requirementIds,
                List.of(), List.of());
    }

    public record RelatedArtifact(
            String artifactType,
            String artifactName,
            String requiredVersion
    ) {
    }
}
