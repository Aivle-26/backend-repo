package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.DocumentStorageProperties;
import com.aivle26.aipm.Dto.CreateProjectDraftFromDocumentsResponse;
import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.PlanningLlmStatus;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDraftFromDocumentsService {
    private static final int MAX_FILE_COUNT = 10;
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "hwp", "hwpx", "docx", "txt", "md", "csv");
    private static final Set<String> STRICT_MIME_EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "csv");
    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "txt", Set.of("text/plain"),
            "md", Set.of("text/markdown", "text/plain"),
            "csv", Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain")
    );

    private final PlanningAgentClient planningAgentClient;
    private final ProjectPmResolver projectPmResolver;
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository requiredArtifactRepository;
    private final ProjectKeyFeatureRepository keyFeatureRepository;
    private final ProjectPlanningExtractionRepository extractionRepository;
    private final DocumentStorageProperties documentStorageProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CreateProjectDraftFromDocumentsResponse createDraftFromDocuments(List<MultipartFile> files, boolean enableLlm, String pmEmployeeNumber) {
        List<ValidatedUploadFile> validatedFiles = validateFiles(files);
        PlanningDocumentExtractResponse agentResponse = planningAgentClient.extractDocuments(files, enableLlm);
        ValidatedAgentResult validatedAgentResult = validateAgentResponse(agentResponse, validatedFiles);
        // TODO: 동일 문서 세트 재요청 시 프로젝트 중복 생성을 방지하기 위한 Idempotency-Key 저장 적용
        return transactionTemplate.execute(status -> saveDraft(validatedFiles, validatedAgentResult, pmEmployeeNumber));
    }

    private List<ValidatedUploadFile> validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_DOCUMENT_REQUIRED", "문서를 하나 이상 업로드해야 합니다.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_PROJECT_DOCUMENTS", "문서는 최대 10개까지 업로드할 수 있습니다.");
        }

        Set<String> fileNames = new HashSet<>();
        List<ValidatedUploadFile> validatedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_DOCUMENT_EMPTY", "빈 파일은 업로드할 수 없습니다.");
            }
            String fileName = normalizeFileName(file.getOriginalFilename());
            if (!fileNames.add(fileName)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_PROJECT_DOCUMENT_NAME", "중복된 파일명은 업로드할 수 없습니다: " + fileName);
            }
            String extension = extractExtension(fileName);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "지원하지 않는 파일 형식입니다: ." + extension);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PROJECT_DOCUMENT_TOO_LARGE", "파일 크기는 20MB를 초과할 수 없습니다: " + fileName);
            }
            validateMimeType(extension, file.getContentType(), fileName);
            validatedFiles.add(new ValidatedUploadFile(file, fileName, extension, file.getContentType(), file.getSize()));
        }
        return validatedFiles;
    }

    private ValidatedAgentResult validateAgentResponse(PlanningDocumentExtractResponse response, List<ValidatedUploadFile> files) {
        if (response == null || response.projectInfo() == null) {
            throw invalidAgentResponse();
        }
        PlanningDocumentExtractResponse.ProjectInfo projectInfo = response.projectInfo();
        requireText(projectInfo.projectName(), "project_name");
        requireText(projectInfo.projectGoal(), "project_goal");
        validateDateRange(projectInfo.periodStart(), projectInfo.periodEnd());
        PlanningLlmStatus llmStatus = parseEnum(response.llmStatus(), PlanningLlmStatus.class, "llm_status");

        List<PlanningDocumentExtractResponse.RequiredArtifact> artifacts = requireList(projectInfo.requiredArtifacts(), "required_artifacts");
        List<RequiredArtifactType> artifactTypes = new ArrayList<>();
        Set<String> artifactKeys = new HashSet<>();
        for (PlanningDocumentExtractResponse.RequiredArtifact artifact : artifacts) {
            requireText(artifact.artifactType(), "artifact_type");
            requireText(artifact.artifactName(), "artifact_name");
            requireText(artifact.requiredVersion(), "required_version");
            RequiredArtifactType type = parseEnum(artifact.artifactType(), RequiredArtifactType.class, "artifact_type");
            if (!artifactKeys.add(type.name() + "|" + artifact.artifactName().trim())) {
                throw invalidAgentResponse("중복된 산출물입니다: " + artifact.artifactName());
            }
            artifactTypes.add(type);
        }

        Map<String, ValidatedUploadFile> uploadByName = new LinkedHashMap<>();
        for (ValidatedUploadFile file : files) {
            uploadByName.put(file.fileName(), file);
        }

        Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName = new HashMap<>();
        for (PlanningDocumentExtractResponse.DocumentResult document : requireList(response.documents(), "documents")) {
            requireText(document.fileName(), "file_name");
            if (!uploadByName.containsKey(document.fileName())) {
                throw invalidAgentResponse("응답 문서명이 실제 업로드 파일과 일치하지 않습니다: " + document.fileName());
            }
            requireText(document.fileType(), "file_type");
            requireText(document.processingMode(), "processing_mode");
            if (document.characterCount() == null || document.characterCount() < 0) {
                throw invalidAgentResponse("character_count가 올바르지 않습니다.");
            }
            documentByName.put(document.fileName(), document);
        }
        if (!documentByName.keySet().equals(uploadByName.keySet())) {
            throw invalidAgentResponse("AI 응답 문서 목록이 업로드 파일 목록과 일치하지 않습니다.");
        }

        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = requireList(response.requirementCandidates(), "requirement_candidates");
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
                throw invalidAgentResponse("중복된 requirement_id입니다: " + requirement.requirementId());
            }
            if (!uploadByName.containsKey(requirement.sourceDocument())) {
                throw invalidAgentResponse("요구사항의 원본 문서명이 업로드 파일과 일치하지 않습니다: " + requirement.sourceDocument());
            }
            requirementTypes.add(parseEnum(requirement.category(), RequirementType.class, "category"));
            requirementPriorities.add(parseEnum(requirement.priority(), RequirementPriority.class, "priority"));
        }

        return new ValidatedAgentResult(response, llmStatus, artifactTypes, documentByName, requirementTypes, requirementPriorities);
    }

    protected CreateProjectDraftFromDocumentsResponse saveDraft(List<ValidatedUploadFile> files, ValidatedAgentResult result, String pmEmployeeNumber) {
        User pm = projectPmResolver.resolve(pmEmployeeNumber);
        List<Path> savedPaths = new ArrayList<>();
        try {
            PlanningDocumentExtractResponse.ProjectInfo projectInfo = result.response().projectInfo();
            Project project = new Project();
            project.setName(projectInfo.projectName().trim());
            project.setDescription(projectInfo.projectGoal().trim());
            project.setClientOrganization(trimToNull(projectInfo.clientOrganization()));
            project.setPlannedStartDate(projectInfo.periodStart());
            project.setPlannedEndDate(projectInfo.periodEnd());
            project.setStatus(ProjectStatus.DRAFT);
            project.setPm(pm);
            project.setAcceptanceConditionsJson(toNullableJson(projectInfo.acceptanceConditions()));
            project.setBudgetContractConditionsJson(toNullableJson(projectInfo.budgetContractConditions()));
            project.setSecurityPrivacyConditionsJson(toNullableJson(projectInfo.securityPrivacyConditions()));
            Project savedProject = projectRepository.save(project);

            List<ProjectDocument> savedDocuments = new ArrayList<>();
            for (ValidatedUploadFile uploadFile : files) {
                StoredFile storedFile = storeFile(uploadFile);
                savedPaths.add(storedFile.path());
                PlanningDocumentExtractResponse.DocumentResult documentResult = result.documentByName().get(uploadFile.fileName());
                ProjectDocument document = new ProjectDocument();
                document.setProject(savedProject);
                document.setStatus(ProjectDocumentStatus.ANALYZED);
                document.setOriginalFileName(uploadFile.fileName());
                document.setStoredFileName(storedFile.storedFileName());
                document.setStoragePath(storedFile.path().toString());
                document.setExtension(uploadFile.extension());
                document.setContentType(uploadFile.contentType());
                document.setFileSize(uploadFile.fileSize());
                document.setFileType(documentResult.fileType().trim());
                document.setCharacterCount(documentResult.characterCount());
                document.setProcessingMode(documentResult.processingMode().trim());
                savedDocuments.add(document);
            }
            savedDocuments = projectDocumentRepository.saveAll(savedDocuments);
            Map<String, ProjectDocument> savedDocumentByName = new HashMap<>();
            for (ProjectDocument document : savedDocuments) {
                savedDocumentByName.put(document.getOriginalFileName(), document);
            }

            List<ProjectKeyFeature> features = new ArrayList<>();
            for (String feature : requireList(projectInfo.keyFeatures(), "key_features")) {
                requireText(feature, "key_features");
                ProjectKeyFeature keyFeature = new ProjectKeyFeature();
                keyFeature.setProject(savedProject);
                keyFeature.setFeatureName(feature.trim());
                features.add(keyFeature);
            }
            keyFeatureRepository.saveAll(features);

            List<ProjectRequiredArtifact> artifacts = new ArrayList<>();
            for (int i = 0; i < result.response().projectInfo().requiredArtifacts().size(); i++) {
                PlanningDocumentExtractResponse.RequiredArtifact artifactResponse = result.response().projectInfo().requiredArtifacts().get(i);
                ProjectRequiredArtifact artifact = new ProjectRequiredArtifact();
                artifact.setProject(savedProject);
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
                requirement.setProject(savedProject);
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
            extraction.setProject(savedProject);
            extraction.setLlmStatus(result.llmStatus());
            extraction.setDocumentCount(savedDocuments.size());
            extraction.setRequirementCount(requirements.size());
            extraction.setRequiredArtifactCount(artifacts.size());
            extractionRepository.save(extraction);

            return new CreateProjectDraftFromDocumentsResponse(
                    savedProject.getId(),
                    savedProject.getName(),
                    savedProject.getStatus(),
                    result.llmStatus(),
                    requirements.size(),
                    artifacts.size(),
                    savedDocuments.size(),
                    "AI 문서 분석 결과를 기반으로 임시 프로젝트가 생성되었습니다."
            );
        } catch (RuntimeException exception) {
            cleanupFiles(savedPaths);
            throw exception;
        }
    }

    private StoredFile storeFile(ValidatedUploadFile uploadFile) {
        Path rootDirectory = Path.of(documentStorageProperties.getStoragePath()).toAbsolutePath().normalize();
        String storedFileName = UUID.randomUUID() + "." + uploadFile.extension();
        Path targetPath = rootDirectory.resolve(storedFileName).normalize();
        if (!targetPath.startsWith(rootDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "파일 경로가 올바르지 않습니다.");
        }
        try {
            Files.createDirectories(rootDirectory);
            try (InputStream inputStream = uploadFile.file().getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(storedFileName, targetPath);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT_DOCUMENT_SAVE_FAILED", "파일 저장에 실패했습니다.", exception);
        }
    }

    private void validateMimeType(String extension, String contentType, String fileName) {
        if (!STRICT_MIME_EXTENSIONS.contains(extension)) {
            return;
        }
        Set<String> allowedMimeTypes = ALLOWED_MIME_TYPES.get(extension);
        if (contentType == null || allowedMimeTypes == null || allowedMimeTypes.stream().noneMatch(contentType::equalsIgnoreCase)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "파일명이 올바르지 않습니다.");
        }
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFC).replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName) || baseName.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "파일명이 올바르지 않습니다.");
        }
        return baseName;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "지원하지 않는 파일 형식입니다: " + fileName);
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalidAgentResponse("프로젝트 종료일이 시작일보다 빠릅니다.");
        }
    }

    private <T> List<T> requireList(List<T> value, String fieldName) {
        if (value == null) {
            throw invalidAgentResponse(fieldName + "이 누락되었습니다.");
        }
        return value;
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidAgentResponse(fieldName + "이 누락되었습니다.");
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String fieldName) {
        requireText(value, fieldName);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalidAgentResponse(fieldName + " 값이 올바르지 않습니다: " + value);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        return invalidAgentResponse("문서 분석 결과 형식이 올바르지 않습니다.");
    }

    private ApiException invalidAgentResponse(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PLANNING_AGENT_RESPONSE", message);
    }

    private void cleanupFiles(List<Path> savedPaths) {
        for (Path path : savedPaths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    private record ValidatedUploadFile(MultipartFile file, String fileName, String extension, String contentType, long fileSize) {
    }

    private record StoredFile(String storedFileName, Path path) {
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
