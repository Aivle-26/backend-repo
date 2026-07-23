package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.CreateProjectDraftFromDocumentsResponse;
import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.PlanningLlmStatus;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.ProjectKeyFeature;
import com.aivle26.aipm.Entity.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.ProjectRequirement;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.RequirementPriority;
import com.aivle26.aipm.Entity.RequirementStatus;
import com.aivle26.aipm.Entity.RequirementType;
import com.aivle26.aipm.Entity.RequiredArtifactType;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDraftFromDocumentsService {
    private final PlanningAgentClient planningAgentClient;
    private final ProjectPmResolver projectPmResolver;
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository requiredArtifactRepository;
    private final ProjectKeyFeatureRepository keyFeatureRepository;
    private final ProjectPlanningExtractionRepository extractionRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectDocumentService projectDocumentService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CreateProjectDraftFromDocumentsResponse createDraftFromDocuments(List<MultipartFile> files, boolean enableLlm, String pmEmployeeNumber) {
        List<ProjectDocumentService.ValidatedUploadFile> validatedFiles = projectDocumentService.validateUploadFiles(files);
        DraftProjectContext draftContext = transactionTemplate.execute(status -> createDraftWithStoredDocuments(validatedFiles, pmEmployeeNumber));

        try {
            List<StoredDocumentFile> storedFiles = projectDocumentService.getStoredDocumentFiles(draftContext.projectId());
            PlanningDocumentExtractResponse agentResponse = planningAgentClient.extractDocuments(storedFiles, enableLlm);
            ValidatedAgentResult validatedAgentResult = validateAgentResponse(agentResponse, validatedFiles);
            return transactionTemplate.execute(status -> finalizeDraft(draftContext.projectId(), validatedAgentResult));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> cleanupFailedDraft(draftContext.projectId()));
            throw exception;
        }
    }

    private DraftProjectContext createDraftWithStoredDocuments(List<ProjectDocumentService.ValidatedUploadFile> files, String pmEmployeeNumber) {
        User pm = projectPmResolver.resolve(pmEmployeeNumber);
        Project project = new Project();
        project.setName("Document Analysis Pending");
        project.setDescription("Document analysis in progress.");
        project.setStatus(ProjectStatus.DRAFT);
        project.setPm(pm);

        Project savedProject = projectRepository.save(project);
        projectDocumentService.replaceProjectDocuments(savedProject, files, ProjectDocumentStatus.UPLOADED);
        return new DraftProjectContext(savedProject.getId());
    }

    private void cleanupFailedDraft(Long projectId) {
        List<ProjectDocument> documents = projectDocumentService.getProjectDocuments(projectId);
        projectDocumentService.cleanupStoredFiles(documents);
        projectDocumentService.deleteProjectDocumentRecords(projectId);
        projectRepository.deleteById(projectId);
    }

    private CreateProjectDraftFromDocumentsResponse finalizeDraft(Long projectId, ValidatedAgentResult result) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
        List<ProjectDocument> savedDocuments = projectDocumentRepository.findByProjectId(projectId);
        if (savedDocuments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }

        PlanningDocumentExtractResponse.ProjectInfo projectInfo = result.response().projectInfo();
        project.setName(projectInfo.projectName().trim());
        project.setDescription(projectInfo.projectGoal().trim());
        project.setClientOrganization(trimToNull(projectInfo.clientOrganization()));
        project.setPlannedStartDate(projectInfo.periodStart());
        project.setPlannedEndDate(projectInfo.periodEnd());
        project.setAcceptanceConditionsJson(toNullableJson(projectInfo.acceptanceConditions()));
        project.setBudgetContractConditionsJson(toNullableJson(projectInfo.budgetContractConditions()));
        project.setSecurityPrivacyConditionsJson(toNullableJson(projectInfo.securityPrivacyConditions()));

        ProjectDocumentAnalysisResult savedAnalysisResult = analysisResultRepository.save(createAnalysisResult(project, projectInfo));

        Map<String, ProjectDocument> savedDocumentByName = new HashMap<>();
        for (ProjectDocument document : savedDocuments) {
            PlanningDocumentExtractResponse.DocumentResult documentResult = result.documentByName().get(document.getOriginalFileName());
            document.setStatus(ProjectDocumentStatus.ANALYZED);
            document.setFileType(documentResult.fileType().trim());
            document.setCharacterCount(documentResult.characterCount());
            document.setProcessingMode(documentResult.processingMode().trim());
            savedDocumentByName.put(document.getOriginalFileName(), document);
        }

        List<ProjectKeyFeature> features = new ArrayList<>();
        for (String feature : requireList(projectInfo.keyFeatures(), "key_features")) {
            requireText(feature, "key_features");
            ProjectKeyFeature keyFeature = new ProjectKeyFeature();
            keyFeature.setProject(project);
            keyFeature.setFeatureName(feature.trim());
            features.add(keyFeature);
        }
        keyFeatureRepository.saveAll(features);

        List<ProjectRequiredArtifact> artifacts = new ArrayList<>();
        for (int i = 0; i < projectInfo.requiredArtifacts().size(); i++) {
            PlanningDocumentExtractResponse.RequiredArtifact artifactResponse = projectInfo.requiredArtifacts().get(i);
            ProjectRequiredArtifact artifact = new ProjectRequiredArtifact();
            artifact.setProject(project);
            artifact.setArtifactType(result.artifactTypes().get(i));
            artifact.setArtifactName(artifactResponse.artifactName().trim());
            artifact.setRequiredVersion(artifactResponse.requiredVersion().trim());
            artifacts.add(artifact);
        }
        requiredArtifactRepository.saveAll(artifacts);

        List<ProjectRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < result.response().requirementCandidates().size(); i++) {
            PlanningDocumentExtractResponse.RequirementCandidate candidate = result.response().requirementCandidates().get(i);
            ProjectRequirement requirement = new ProjectRequirement();
            requirement.setProject(project);
            requirement.setAnalysisResult(savedAnalysisResult);
            requirement.setSourceDocument(savedDocumentByName.get(candidate.sourceDocument()));
            requirement.setExternalReferenceId(candidate.requirementId().trim());
            requirement.setType(result.requirementTypes().get(i));
            requirement.setTitle(candidate.functionName().trim());
            requirement.setDescription(candidate.requirementText().trim());
            requirement.setPriority(result.requirementPriorities().get(i));
            requirement.setStatus(RequirementStatus.UNCONFIRMED);
            requirement.setConfirmed(false);
            requirement.setAcceptanceCriteria(trimToNull(candidate.acceptanceCriteria()));
            requirement.setDueDate(candidate.dueDate());
            requirement.setDeliverableName(trimToNull(candidate.deliverableName()));
            requirement.setSecurityCondition(trimToNull(candidate.securityCondition()));
            requirement.setSourceDocumentName(candidate.sourceDocument().trim());
            requirement.setSourceExcerpt(trimToNull(candidate.sourceExcerpt()));
            requirements.add(requirement);
        }
        projectRequirementRepository.saveAll(requirements);

        ProjectPlanningExtraction extraction = new ProjectPlanningExtraction();
        extraction.setProject(project);
        extraction.setLlmStatus(result.llmStatus());
        extraction.setDocumentCount(savedDocuments.size());
        extraction.setRequirementCount(requirements.size());
        extraction.setRequiredArtifactCount(artifacts.size());
        extractionRepository.save(extraction);

        return new CreateProjectDraftFromDocumentsResponse(
                project.getId(),
                project.getName(),
                project.getStatus(),
                result.llmStatus(),
                requirements.size(),
                artifacts.size(),
                savedDocuments.size(),
                "Project draft created from analyzed documents."
        );
    }

    private ValidatedAgentResult validateAgentResponse(
            PlanningDocumentExtractResponse response,
            List<ProjectDocumentService.ValidatedUploadFile> files
    ) {
        if (response == null || response.projectInfo() == null) {
            throw invalidAgentResponse();
        }

        PlanningDocumentExtractResponse normalizedResponse = normalizeResponseFileNames(response, files);
        PlanningDocumentExtractResponse.ProjectInfo projectInfo = normalizedResponse.projectInfo();
        requireText(projectInfo.projectName(), "project_name");
        requireText(projectInfo.projectGoal(), "project_goal");
        validateDateRange(projectInfo.periodStart(), projectInfo.periodEnd());
        PlanningLlmStatus llmStatus = parseEnum(normalizedResponse.llmStatus(), PlanningLlmStatus.class, "llm_status");

        List<PlanningDocumentExtractResponse.RequiredArtifact> artifacts = requireList(projectInfo.requiredArtifacts(), "required_artifacts");
        List<RequiredArtifactType> artifactTypes = new ArrayList<>();
        Set<String> artifactKeys = new HashSet<>();
        for (PlanningDocumentExtractResponse.RequiredArtifact artifact : artifacts) {
            requireText(artifact.artifactType(), "artifact_type");
            requireText(artifact.artifactName(), "artifact_name");
            requireText(artifact.requiredVersion(), "required_version");
            RequiredArtifactType type = parseEnum(artifact.artifactType(), RequiredArtifactType.class, "artifact_type");
            if (!artifactKeys.add(type.name() + "|" + artifact.artifactName().trim())) {
                throw invalidAgentResponse("Duplicate required_artifact: " + artifact.artifactName());
            }
            artifactTypes.add(type);
        }

        Map<String, ProjectDocumentService.ValidatedUploadFile> uploadByName = new LinkedHashMap<>();
        for (ProjectDocumentService.ValidatedUploadFile file : files) {
            uploadByName.put(file.originalFileName(), file);
        }

        Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName = new HashMap<>();
        for (PlanningDocumentExtractResponse.DocumentResult document : requireList(normalizedResponse.documents(), "documents")) {
            requireText(document.fileName(), "file_name");
            requireText(document.fileType(), "file_type");
            requireText(document.processingMode(), "processing_mode");
            if (document.characterCount() == null || document.characterCount() < 0) {
                throw invalidAgentResponse("character_count is invalid.");
            }
            documentByName.put(document.fileName(), document);
        }
        if (!documentByName.keySet().equals(uploadByName.keySet())) {
            throw invalidAgentResponse("AI response document list does not match uploaded files.");
        }

        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = requireList(normalizedResponse.requirementCandidates(), "requirement_candidates");
        Set<String> requirementIds = new HashSet<>();
        List<RequirementType> requirementTypes = new ArrayList<>();
        List<RequirementPriority> requirementPriorities = new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementCandidate requirement : requirements) {
            requireText(requirement.requirementId(), "requirement_id");
            requireText(requirement.functionName(), "function_name");
            requireText(requirement.requirementText(), "requirement_text");
            requireText(requirement.category(), "category");
            requireText(requirement.priority(), "priority");
            requireText(requirement.sourceDocument(), "source_document");
            if (!requirementIds.add(requirement.requirementId().trim())) {
                throw invalidAgentResponse("Duplicate requirement_id: " + requirement.requirementId());
            }
            requirementTypes.add(parseEnum(requirement.category(), RequirementType.class, "category"));
            requirementPriorities.add(parseEnum(requirement.priority(), RequirementPriority.class, "priority"));
        }

        return new ValidatedAgentResult(normalizedResponse, llmStatus, artifactTypes, documentByName, requirementTypes, requirementPriorities);
    }

    private PlanningDocumentExtractResponse normalizeResponseFileNames(
            PlanningDocumentExtractResponse response,
            List<ProjectDocumentService.ValidatedUploadFile> files
    ) {
        List<PlanningDocumentExtractResponse.DocumentResult> responseDocuments = requireList(response.documents(), "documents");
        if (responseDocuments.size() != files.size()) {
            throw invalidAgentResponse("AI response document count does not match uploaded files.");
        }

        Map<String, String> responseToUploadNames = new LinkedHashMap<>();
        List<PlanningDocumentExtractResponse.DocumentResult> normalizedDocuments = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            PlanningDocumentExtractResponse.DocumentResult document = responseDocuments.get(i);
            requireText(document.fileName(), "file_name");
            String responseFileName = normalizeFileName(document.fileName());
            String uploadFileName = files.get(i).originalFileName();
            if (responseToUploadNames.putIfAbsent(responseFileName, uploadFileName) != null) {
                throw invalidAgentResponse("Duplicate AI document file_name: " + responseFileName);
            }
            normalizedDocuments.add(new PlanningDocumentExtractResponse.DocumentResult(
                    uploadFileName,
                    document.fileType(),
                    document.characterCount(),
                    document.processingMode()
            ));
        }

        List<PlanningDocumentExtractResponse.RequirementCandidate> normalizedRequirements = new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementCandidate requirement : requireList(response.requirementCandidates(), "requirement_candidates")) {
            requireText(requirement.sourceDocument(), "source_document");
            String sourceDocument = normalizeFileName(requirement.sourceDocument());
            String uploadFileName = responseToUploadNames.get(sourceDocument);
            if (uploadFileName == null) {
                throw invalidAgentResponse("Requirement source_document is not mapped to an uploaded file: " + requirement.sourceDocument());
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
                    uploadFileName,
                    requirement.sourceExcerpt()
            ));
        }

        return new PlanningDocumentExtractResponse(
                response.projectInfo(),
                normalizedRequirements,
                normalizedDocuments,
                response.llmStatus()
        );
    }

    private ProjectDocumentAnalysisResult createAnalysisResult(Project project, PlanningDocumentExtractResponse.ProjectInfo projectInfo) {
        ProjectDocumentAnalysisResult analysisResult = new ProjectDocumentAnalysisResult();
        analysisResult.setProject(project);
        analysisResult.setAgentExecutionId("draft-" + UUID.randomUUID());
        analysisResult.setAgentVersion("planning-agent:draft-from-documents");
        analysisResult.setProjectGoal(projectInfo.projectGoal().trim());
        analysisResult.setScope(buildScope(projectInfo));
        analysisResult.setDeliverablesJson(toStringListJson(extractArtifactNames(projectInfo.requiredArtifacts())));
        analysisResult.setMilestonesJson(emptyJsonArray());
        analysisResult.setTechnologyStacksJson(emptyJsonArray());
        analysisResult.setConstraintsJson(toStringListJson(defaultIfNull(projectInfo.budgetContractConditions())));
        analysisResult.setRisksJson(toStringListJson(defaultIfNull(projectInfo.securityPrivacyConditions())));
        return analysisResult;
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "Invalid file name.");
        }
        String normalized = java.text.Normalizer.normalize(fileName, java.text.Normalizer.Form.NFC).replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName) || baseName.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "Invalid file name.");
        }
        return baseName;
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalidAgentResponse("period_end is before period_start.");
        }
    }

    private <T> List<T> requireList(List<T> value, String fieldName) {
        if (value == null) {
            throw invalidAgentResponse(fieldName + " is missing.");
        }
        return value;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidAgentResponse(fieldName + " is missing.");
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String fieldName) {
        requireText(value, fieldName);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalidAgentResponse(fieldName + " is invalid: " + value);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildScope(PlanningDocumentExtractResponse.ProjectInfo projectInfo) {
        List<String> keyFeatures = defaultIfNull(projectInfo.keyFeatures()).stream()
                .map(this::trimToNull)
                .filter(value -> value != null)
                .toList();
        if (!keyFeatures.isEmpty()) {
            return String.join(", ", keyFeatures);
        }
        return projectInfo.projectGoal().trim();
    }

    private List<String> extractArtifactNames(List<PlanningDocumentExtractResponse.RequiredArtifact> artifacts) {
        List<String> artifactNames = new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequiredArtifact artifact : defaultIfNull(artifacts)) {
            String artifactName = trimToNull(artifact.artifactName());
            if (artifactName != null) {
                artifactNames.add(artifactName);
            }
        }
        return artifactNames;
    }

    private <T> List<T> defaultIfNull(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String emptyJsonArray() {
        return "[]";
    }

    private String toStringListJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidAgentResponse();
        }
    }

    private String toNullableJson(List<String> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidAgentResponse();
        }
    }

    private ApiException invalidAgentResponse() {
        return invalidAgentResponse("Planning agent response is invalid.");
    }

    private ApiException invalidAgentResponse(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PLANNING_AGENT_RESPONSE", message);
    }

    private record DraftProjectContext(Long projectId) {
    }

    private record ValidatedAgentResult(
            PlanningDocumentExtractResponse response,
            PlanningLlmStatus llmStatus,
            List<RequiredArtifactType> artifactTypes,
            Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName,
            List<RequirementType> requirementTypes,
            List<RequirementPriority> requirementPriorities
    ) {
    }
}
