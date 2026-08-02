package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.WbsDifficulty;
import com.aivle26.aipm.Entity.project.WbsPhase;
import com.aivle26.aipm.Entity.project.WbsSkill;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ProjectWbsResponse(
        Long wbsResultId,
        Long projectId,
        String agentExecutionId,
        String agentVersion,
        AiStatus aiStatus,
        Coverage coverage,
        boolean finalConfirmed,
        LocalDateTime createdAt,
        List<WbsTaskDetail> aiSuggestionTasks,
        List<WbsTaskDetail> finalTasks
) {
    public record WbsTaskDetail(
            Long taskId,
            String externalTaskId,
            String parentExternalTaskId,
            String taskCode,
            String taskName,
            String description,
            WbsPhase phase,
            Set<WbsSkill> requiredSkills,
            WbsDifficulty difficulty,
            int estimatedHours,
            int orderIndex,
            List<Long> requirementIds,
            List<WbsTaskResultRequest.RelatedArtifact> relatedArtifacts,
            List<String> completionCriteria,
            boolean confirmed
    ) {
    }

    public record AiStatus(
            String llmStatus,
            String generationStatus,
            List<String> warnings
    ) {
    }

    public record Coverage(
            RequirementCoverage requirements,
            ArtifactCoverage artifacts
    ) {
    }

    public record RequirementCoverage(
            int total,
            int mapped,
            List<Long> unmappedRequirementIds,
            double coverageRate
    ) {
    }

    public record ArtifactCoverage(
            int total,
            int mapped,
            List<String> unmappedArtifactTypes,
            double coverageRate
    ) {
    }
}
