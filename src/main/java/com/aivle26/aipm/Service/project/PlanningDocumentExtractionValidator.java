package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Entity.project.PlanningLlmStatus;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PlanningDocumentExtractionValidator {

    public ValidatedResult validateForRequirementAnalysis(
            PlanningDocumentExtractResponse response,
            List<String> originalFileNames,
            String existingProjectName,
            String existingProjectGoal
    ) {
        if (response == null || response.projectInfo() == null) {
            throw invalidResponse();
        }

        PlanningDocumentExtractResponse.ProjectInfo projectInfo = response.projectInfo();
        String projectName = defaultText(projectInfo.projectName(), existingProjectName);
        String projectGoal = defaultText(
                projectInfo.projectGoal(),
                defaultText(existingProjectGoal, projectName)
        );
        PlanningDocumentExtractResponse normalizedResponse =
                new PlanningDocumentExtractResponse(
                        new PlanningDocumentExtractResponse.ProjectInfo(
                                projectName,
                                projectGoal,
                                projectInfo.clientOrganization(),
                                projectInfo.periodStart(),
                                projectInfo.periodEnd(),
                                projectInfo.keyFeatures(),
                                projectInfo.requiredArtifacts(),
                                projectInfo.acceptanceConditions(),
                                projectInfo.budgetContractConditions(),
                                projectInfo.securityPrivacyConditions()
                        ),
                        response.requirementCandidates(),
                        response.documents(),
                        response.llmStatus()
                );

        return validate(normalizedResponse, originalFileNames);
    }

    public ValidatedResult validate(
            PlanningDocumentExtractResponse response,
            List<String> originalFileNames
    ) {
        if (response == null || response.projectInfo() == null) {
            throw invalidResponse();
        }

        PlanningDocumentExtractResponse normalizedResponse =
                normalizeResponseFileNames(response, originalFileNames);
        PlanningDocumentExtractResponse.ProjectInfo projectInfo = normalizedResponse.projectInfo();
        requireText(projectInfo.projectName(), "project_name");
        requireText(projectInfo.projectGoal(), "project_goal");
        validateDateRange(projectInfo.periodStart(), projectInfo.periodEnd());
        for (String keyFeature : requireList(projectInfo.keyFeatures(), "key_features")) {
            requireText(keyFeature, "key_features");
        }
        PlanningLlmStatus llmStatus =
                parseEnum(normalizedResponse.llmStatus(), PlanningLlmStatus.class, "llm_status");

        List<PlanningDocumentExtractResponse.RequiredArtifact> artifacts =
                requireList(projectInfo.requiredArtifacts(), "required_artifacts");
        List<ProjectArtifactType> artifactTypes = new ArrayList<>();
        Set<String> artifactKeys = new HashSet<>();
        for (PlanningDocumentExtractResponse.RequiredArtifact artifact : artifacts) {
            if (artifact == null) {
                throw invalidResponse("required_artifact is invalid.");
            }
            requireText(artifact.artifactType(), "artifact_type");
            requireText(artifact.artifactName(), "artifact_name");
            requireText(artifact.requiredVersion(), "required_version");
            ProjectArtifactType type =
                    parseEnum(artifact.artifactType(), ProjectArtifactType.class, "artifact_type");
            if (!artifactKeys.add(type.name() + "|" + artifact.artifactName().trim())) {
                throw invalidResponse("Duplicate required_artifact: " + artifact.artifactName());
            }
            artifactTypes.add(type);
        }

        Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName =
                validateDocuments(normalizedResponse, originalFileNames);

        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements =
                requireList(normalizedResponse.requirementCandidates(), "requirement_candidates");
        Set<Long> requirementIds = new HashSet<>();
        List<RequirementType> requirementTypes = new ArrayList<>();
        List<RequirementPriority> requirementPriorities = new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementCandidate requirement : requirements) {
            if (requirement == null) {
                throw invalidResponse("requirement_candidate is invalid.");
            }
            requirePositiveId(requirement.requirementId(), "requirement_id");
            requireText(requirement.functionName(), "function_name");
            requireText(requirement.requirementText(), "requirement_text");
            requireText(requirement.category(), "category");
            requireText(requirement.priority(), "priority");
            requireText(requirement.sourceDocument(), "source_document");
            if (!requirementIds.add(requirement.requirementId())) {
                throw invalidResponse("Duplicate requirement_id: " + requirement.requirementId());
            }
            requirementTypes.add(
                    parseEnum(requirement.category(), RequirementType.class, "category")
            );
            requirementPriorities.add(
                    parseEnum(requirement.priority(), RequirementPriority.class, "priority")
            );
        }

        return new ValidatedResult(
                normalizedResponse,
                llmStatus,
                artifactTypes,
                documentByName,
                requirementTypes,
                requirementPriorities
        );
    }

    private Map<String, PlanningDocumentExtractResponse.DocumentResult> validateDocuments(
            PlanningDocumentExtractResponse response,
            List<String> originalFileNames
    ) {
        Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName =
                new HashMap<>();
        for (PlanningDocumentExtractResponse.DocumentResult document :
                requireList(response.documents(), "documents")) {
            if (document == null) {
                throw invalidResponse("document is invalid.");
            }
            requireText(document.fileName(), "file_name");
            requireText(document.fileType(), "file_type");
            requireText(document.processingMode(), "processing_mode");
            if (document.characterCount() == null || document.characterCount() < 0) {
                throw invalidResponse("character_count is invalid.");
            }
            if (documentByName.put(document.fileName(), document) != null) {
                throw invalidResponse("Duplicate document file_name: " + document.fileName());
            }
        }

        if (!documentByName.keySet().equals(new HashSet<>(originalFileNames))) {
            throw invalidResponse("AI response document list does not match uploaded files.");
        }
        return documentByName;
    }

    private PlanningDocumentExtractResponse normalizeResponseFileNames(
            PlanningDocumentExtractResponse response,
            List<String> originalFileNames
    ) {
        List<PlanningDocumentExtractResponse.DocumentResult> responseDocuments =
                requireList(response.documents(), "documents");
        if (responseDocuments.size() != originalFileNames.size()) {
            throw invalidResponse("AI response document count does not match uploaded files.");
        }

        Map<String, String> responseToOriginalNames = new LinkedHashMap<>();
        List<PlanningDocumentExtractResponse.DocumentResult> normalizedDocuments =
                new ArrayList<>();
        for (int index = 0; index < originalFileNames.size(); index++) {
            PlanningDocumentExtractResponse.DocumentResult document = responseDocuments.get(index);
            if (document == null) {
                throw invalidResponse("document is invalid.");
            }
            requireText(document.fileName(), "file_name");
            String responseFileName = normalizeFileName(document.fileName());
            String originalFileName = originalFileNames.get(index);
            if (responseToOriginalNames.putIfAbsent(responseFileName, originalFileName) != null) {
                throw invalidResponse("Duplicate AI document file_name: " + responseFileName);
            }
            normalizedDocuments.add(new PlanningDocumentExtractResponse.DocumentResult(
                    originalFileName,
                    document.fileType(),
                    document.characterCount(),
                    document.processingMode()
            ));
        }

        List<PlanningDocumentExtractResponse.RequirementCandidate> normalizedRequirements =
                new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementCandidate requirement :
                requireList(response.requirementCandidates(), "requirement_candidates")) {
            if (requirement == null) {
                throw invalidResponse("requirement_candidate is invalid.");
            }
            requireText(requirement.sourceDocument(), "source_document");
            String responseSource = normalizeFileName(requirement.sourceDocument());
            String originalFileName = responseToOriginalNames.get(responseSource);
            if (originalFileName == null) {
                throw invalidResponse(
                        "Requirement source_document is not mapped to an uploaded file: "
                                + requirement.sourceDocument()
                );
            }
            List<PlanningDocumentExtractResponse.RequirementEvidence> normalizedEvidences =
                    new ArrayList<>();
            for (PlanningDocumentExtractResponse.RequirementEvidence evidence :
                    requirement.evidences() == null
                            ? List.<PlanningDocumentExtractResponse.RequirementEvidence>of()
                            : requirement.evidences()) {
                if (evidence == null) {
                    throw invalidResponse("requirement evidence is invalid.");
                }
                String evidenceSource = normalizeFileName(evidence.sourceDocument());
                String evidenceOriginalFileName =
                        responseToOriginalNames.get(evidenceSource);
                if (evidenceOriginalFileName == null) {
                    throw invalidResponse(
                            "Requirement evidence source_document is not mapped to an uploaded file: "
                                    + evidence.sourceDocument()
                    );
                }
                normalizedEvidences.add(
                        new PlanningDocumentExtractResponse.RequirementEvidence(
                                evidence.documentId(),
                                evidenceOriginalFileName,
                                evidence.pageNumber(),
                                evidence.chunkId(),
                                evidence.quoteText(),
                                evidence.startOffset(),
                                evidence.endOffset(),
                                evidence.boundingBoxes()
                        )
                );
            }
            normalizedRequirements.add(new PlanningDocumentExtractResponse.RequirementCandidate(
                    requirement.requirementId(),
                    requirement.functionName(),
                    requirement.requirementText(),
                    requirement.category(),
                    requirement.priority(),
                    requirement.acceptanceCriteria(),
                    requirement.dueDate(),
                    requirement.deliverableName(),
                    requirement.securityCondition(),
                    originalFileName,
                    requirement.sourceExcerpt(),
                    normalizedEvidences
            ));
        }

        return new PlanningDocumentExtractResponse(
                response.projectInfo(),
                normalizedRequirements,
                normalizedDocuments,
                response.llmStatus()
        );
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw invalidResponse("file_name is missing.");
        }
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFC)
                .replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank()
                || ".".equals(baseName)
                || "..".equals(baseName)
                || baseName.contains("..")) {
            throw invalidResponse("file_name is invalid.");
        }
        return baseName;
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalidResponse("period_end is before period_start.");
        }
    }

    private <T> List<T> requireList(List<T> value, String fieldName) {
        if (value == null) {
            throw invalidResponse(fieldName + " is missing.");
        }
        return value;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidResponse(fieldName + " is missing.");
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw invalidResponse(fieldName + " must be a positive integer.");
        }
    }

    private <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> enumType,
            String fieldName
    ) {
        requireText(value, fieldName);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalidResponse(fieldName + " is invalid: " + value);
        }
    }

    private ApiException invalidResponse() {
        return invalidResponse("Planning agent response is invalid.");
    }

    private ApiException invalidResponse(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_AGENT_RESPONSE",
                message
        );
    }

    public record ValidatedResult(
            PlanningDocumentExtractResponse response,
            PlanningLlmStatus llmStatus,
            List<ProjectArtifactType> artifactTypes,
            Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName,
            List<RequirementType> requirementTypes,
            List<RequirementPriority> requirementPriorities
    ) {
    }
}
