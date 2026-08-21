package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.PlanningLlmStatus;
import com.aivle26.aipm.Entity.project.ProjectStatus;



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
