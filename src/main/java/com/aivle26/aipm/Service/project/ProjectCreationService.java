package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Dto.project.CreateProjectDraftFromDocumentsResponse;
import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.project.PlanningLlmStatus;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectKeyFeature;
import com.aivle26.aipm.Entity.project.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.user.UserRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class ProjectCreationService {
    private final PlanningAgentClient planningAgentClient;
    private final UserRepository userRepository;
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
    private final ProjectAuthorizationService projectAuthorizationService;

    // 프로젝트 입력값과 PM을 검증해 DRAFT 프로젝트를 저장하고 생성 결과를 반환한다.
    @Transactional
    public CreateProjectDraftResponse createProjectDraft(CreateProjectDraftRequest request) {
        projectAuthorizationService.requireCurrentPm(request.pmEmployeeNumber());
        LocalDate plannedStartDate = request.plannedStartDate();
        LocalDate plannedEndDate = request.plannedEndDate();
        if (plannedEndDate.isBefore(plannedStartDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }

        // 요청 데이터를 프로젝트 엔티티로 매핑
        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description() == null ? null : request.description().trim());
        project.setPm(resolvePm(request.pmEmployeeNumber()));
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(plannedStartDate);
        project.setPlannedEndDate(plannedEndDate);

        Project savedProject = projectRepository.save(project);

        // 저장된 프로젝트를 생성 응답 DTO로 매핑
        return new CreateProjectDraftResponse(
                savedProject.getId(),
                savedProject.getName(),
                savedProject.getPm().getEmployeeNumber(),
                savedProject.getStatus(),
                savedProject.getPlannedStartDate(),
                savedProject.getPlannedEndDate()
        );
    }

    // 업로드 파일을 한 번 저장한 뒤 저장 파일의 AI 추출 결과로 프로젝트 초안을 완성한다.
    public CreateProjectDraftFromDocumentsResponse createDraftFromDocuments(List<MultipartFile> files, boolean enableLlm, String pmEmployeeNumber) {
        projectAuthorizationService.requireCurrentPm(pmEmployeeNumber);
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

    // 검증된 파일과 PM 사번으로 임시 프로젝트 및 문서 레코드를 저장해 프로젝트 ID를 반환한다.
    private DraftProjectContext createDraftWithStoredDocuments(List<ProjectDocumentService.ValidatedUploadFile> files, String pmEmployeeNumber) {
        // 업로드 문서를 저장할 프로젝트 초안 생성
        Project project = new Project();
        project.setName("Document Analysis Pending");
        project.setDescription("Document analysis in progress.");
        project.setStatus(ProjectStatus.DRAFT);
        project.setPm(resolvePm(pmEmployeeNumber));

        Project savedProject = projectRepository.save(project);
        projectDocumentService.replaceProjectDocuments(savedProject, files, ProjectDocumentStatus.UPLOADED);
        return new DraftProjectContext(savedProject.getId());
    }

    // 문서 기반 초안 생성 실패 시 해당 프로젝트의 저장 파일과 DB 레코드를 정리한다.
    private void cleanupFailedDraft(Long projectId) {
        List<ProjectDocument> documents = projectDocumentService.getProjectDocuments(projectId);
        projectDocumentService.cleanupStoredFiles(documents);
        projectDocumentService.deleteProjectDocumentRecords(projectId);
        projectRepository.deleteById(projectId);
    }

    // 검증된 AI 결과를 프로젝트·문서·요구사항·산출물에 저장하고 생성 응답을 조립한다.
    private CreateProjectDraftFromDocumentsResponse finalizeDraft(Long projectId, ValidatedAgentResult result) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
        List<ProjectDocument> savedDocuments = projectDocumentRepository.findByProjectId(projectId);
        if (savedDocuments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }

        PlanningDocumentExtractResponse.ProjectInfo projectInfo = result.response().projectInfo();

        // AI 분석 결과를 프로젝트 엔티티에 매핑
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
            requirement.setExternalReferenceId(candidate.requirementId());
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

    // 사번으로 PM 역할 사용자를 조회·검증해 프로젝트 담당자 엔티티를 반환한다.
    private User resolvePm(String pmEmployeeNumber) {
        return userRepository.findByEmployeeNumber(pmEmployeeNumber.trim())
                .filter(user -> "PM".equalsIgnoreCase(user.getRole()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pm user not found"));
    }

    // AI 응답의 필수값과 업로드 문서 대응 관계를 검증해 타입 변환 결과를 반환한다.
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
        List<ProjectArtifactType> artifactTypes = new ArrayList<>();
        Set<String> artifactKeys = new HashSet<>();
        for (PlanningDocumentExtractResponse.RequiredArtifact artifact : artifacts) {
            requireText(artifact.artifactType(), "artifact_type");
            requireText(artifact.artifactName(), "artifact_name");
            requireText(artifact.requiredVersion(), "required_version");
            ProjectArtifactType type = parseEnum(artifact.artifactType(), ProjectArtifactType.class, "artifact_type");
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
        Set<Long> requirementIds = new HashSet<>();
        List<RequirementType> requirementTypes = new ArrayList<>();
        List<RequirementPriority> requirementPriorities = new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementCandidate requirement : requirements) {
            requirePositiveId(requirement.requirementId(), "requirement_id");
            requireText(requirement.functionName(), "function_name");
            requireText(requirement.requirementText(), "requirement_text");
            requireText(requirement.category(), "category");
            requireText(requirement.priority(), "priority");
            requireText(requirement.sourceDocument(), "source_document");
            if (!requirementIds.add(requirement.requirementId())) {
                throw invalidAgentResponse("Duplicate requirement_id: " + requirement.requirementId());
            }
            requirementTypes.add(parseEnum(requirement.category(), RequirementType.class, "category"));
            requirementPriorities.add(parseEnum(requirement.priority(), RequirementPriority.class, "priority"));
        }

        return new ValidatedAgentResult(normalizedResponse, llmStatus, artifactTypes, documentByName, requirementTypes, requirementPriorities);
    }

    // AI 파일명을 업로드 원본명과 순서대로 대응시켜 문서 및 요구사항 출처명을 정규화한다.
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

    // 프로젝트 정보와 AI 추출 항목을 분석 결과 엔티티로 변환해 반환한다.
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

    // AI 응답 파일명에서 경로를 제거하고 NFC 형식의 안전한 기본 파일명을 반환한다.
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

    // AI가 반환한 시작일과 종료일의 존재 여부 및 시간 순서를 검증한다.
    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalidAgentResponse("period_end is before period_start.");
        }
    }

    // AI 응답 목록 필드가 null이 아닌지 검증하고 원본 목록을 반환한다.
    private <T> List<T> requireList(List<T> value, String fieldName) {
        if (value == null) {
            throw invalidAgentResponse(fieldName + " is missing.");
        }
        return value;
    }

    // AI 응답 문자열 필드가 공백이 아닌 필수값인지 검증한다.
    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidAgentResponse(fieldName + " is missing.");
        }
    }

    // AI 응답 식별자가 null이 아닌 양수인지 검증한다.
    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw invalidAgentResponse(fieldName + " must be a positive integer.");
        }
    }

    // AI 문자열 값을 지정 Enum으로 변환하고 허용되지 않은 값은 분석 오류로 처리한다.
    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String fieldName) {
        requireText(value, fieldName);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalidAgentResponse(fieldName + " is invalid: " + value);
        }
    }

    // 선택 문자열의 앞뒤 공백을 제거하고 빈 값은 null로 반환한다.
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // AI 프로젝트 정보의 업무 범위와 제외 범위를 결합해 저장용 범위 문자열을 반환한다.
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

    // 필수 산출물 응답에서 공백을 제거한 산출물명 목록을 추출한다.
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

    // 선택 목록이 null이면 빈 목록을, 값이 있으면 원본 목록을 반환한다.
    private <T> List<T> defaultIfNull(List<T> value) {
        return value == null ? List.of() : value;
    }

    // 분석 결과의 값이 없는 배열 필드에 저장할 빈 JSON 배열을 반환한다.
    private String emptyJsonArray() {
        return "[]";
    }

    // 필수 문자열 목록을 JSON 배열 문자열로 직렬화해 반환한다.
    private String toStringListJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidAgentResponse();
        }
    }

    // 선택 문자열 목록을 JSON으로 직렬화하고 값이 없으면 null을 반환한다.
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

    // 상세 사유가 없는 잘못된 AI 응답용 공통 예외를 생성한다.
    private ApiException invalidAgentResponse() {
        return invalidAgentResponse("Planning agent response is invalid.");
    }

    // 검증 사유를 포함한 잘못된 AI 응답용 공통 예외를 생성한다.
    private ApiException invalidAgentResponse(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PLANNING_AGENT_RESPONSE", message);
    }

    private record DraftProjectContext(Long projectId) {
    }

    private record ValidatedAgentResult(
            PlanningDocumentExtractResponse response,
            PlanningLlmStatus llmStatus,
            List<ProjectArtifactType> artifactTypes,
            Map<String, PlanningDocumentExtractResponse.DocumentResult> documentByName,
            List<RequirementType> requirementTypes,
            List<RequirementPriority> requirementPriorities
    ) {
    }
}
