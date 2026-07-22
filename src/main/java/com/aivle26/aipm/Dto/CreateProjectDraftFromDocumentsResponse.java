package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.PlanningLlmStatus;
import com.aivle26.aipm.Entity.ProjectStatus;

public record CreateProjectDraftFromDocumentsResponse(
        Long projectId,
        String projectName,
        ProjectStatus status,
        PlanningLlmStatus llmStatus,
        int requirementCount,
        int requiredArtifactCount,
        int documentCount,
        String message
) {
}
