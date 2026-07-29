package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Dto.project.CreateProjectDraftFromDocumentsResponse;
import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectKeyFeature;
import com.aivle26.aipm.Entity.project.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectCreationService {
    private final PlanningAgentClient planningAgentClient;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectRequiredArtifactRepository requiredArtifactRepository;
    private final ProjectKeyFeatureRepository keyFeatureRepository;
    private final ProjectPlanningExtractionRepository extractionRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectDocumentService projectDocumentService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final PlanningDocumentExtractionValidator extractionValidator;
    private final ProjectRequirementImportService requirementImportService;

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
            PlanningDocumentExtractionValidator.ValidatedResult validatedAgentResult =
                    extractionValidator.validate(
                            agentResponse,
                            validatedFiles.stream()
                                    .map(ProjectDocumentService.ValidatedUploadFile::originalFileName)
                                    .toList()
                    );
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
    private CreateProjectDraftFromDocumentsResponse finalizeDraft(
            Long projectId,
            PlanningDocumentExtractionValidator.ValidatedResult result
    ) {
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
        for (String feature : projectInfo.keyFeatures()) {
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

        List<ProjectRequirement> requirements = requirementImportService.importRequirements(
                project,
                savedAnalysisResult,
                savedDocumentByName,
                result
        );

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

}
