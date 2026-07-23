package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.PlanningLlmStatus;
import com.aivle26.aipm.Entity.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.RequirementPriority;
import com.aivle26.aipm.Entity.RequirementStatus;
import com.aivle26.aipm.Entity.RequirementType;
import com.aivle26.aipm.Entity.RequiredArtifactType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectDocumentAnalysisResultsResponse(
        ProjectDetail project,
        List<DocumentDetail> documents,
        AnalysisResultDetail analysisResult,
        List<RequirementDetail> requirements,
        List<RequiredArtifactDetail> requiredArtifacts,
        List<KeyFeatureDetail> keyFeatures,
        PlanningExtractionDetail planningExtraction
) {
    public record ProjectDetail(
            Long projectId,
            String name,
            String description,
            String clientOrganization,
            String pmEmployeeNumber,
            ProjectStatus status,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            String acceptanceConditionsJson,
            String budgetContractConditionsJson,
            String securityPrivacyConditionsJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record DocumentDetail(
            Long documentId,
            ProjectDocumentStatus status,
            String originalFileName,
            String storedFileName,
            String storagePath,
            String extension,
            String contentType,
            long fileSize,
            Long characterCount,
            String fileType,
            String processingMode,
            LocalDateTime createdAt
    ) {
    }

    public record AnalysisResultDetail(
            Long analysisResultId,
            String agentExecutionId,
            String agentVersion,
            String projectGoal,
            String scope,
            String deliverablesJson,
            String milestonesJson,
            String technologyStacksJson,
            String constraintsJson,
            String risksJson,
            LocalDateTime createdAt
    ) {
    }

    public record RequirementDetail(
            Long requirementId,
            Long analysisResultId,
            Long sourceDocumentId,
            String externalReferenceId,
            RequirementType type,
            String title,
            String description,
            String acceptanceCriteria,
            LocalDate dueDate,
            String deliverableName,
            String securityCondition,
            String sourceDocumentName,
            String sourceExcerpt,
            RequirementPriority priority,
            RequirementStatus status,
            boolean confirmed,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record RequiredArtifactDetail(
            Long requiredArtifactId,
            RequiredArtifactType artifactType,
            String artifactName,
            String requiredVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record KeyFeatureDetail(
            Long keyFeatureId,
            String featureName,
            LocalDateTime createdAt
    ) {
    }

    public record PlanningExtractionDetail(
            Long planningExtractionId,
            PlanningLlmStatus llmStatus,
            int documentCount,
            int requirementCount,
            int requiredArtifactCount,
            LocalDateTime createdAt
    ) {
    }
}
