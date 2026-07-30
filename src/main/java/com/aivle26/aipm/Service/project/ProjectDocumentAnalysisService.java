package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectKeyFeature;
import com.aivle26.aipm.Entity.project.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Mapper.project.ProjectRequirementMapper;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProjectDocumentAnalysisService {
    private static final String ANALYSIS_CONTRACT_VERSION = "requirements-analysis-v1";

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository projectRequiredArtifactRepository;
    private final ProjectKeyFeatureRepository projectKeyFeatureRepository;
    private final ProjectPlanningExtractionRepository projectPlanningExtractionRepository;
    private final ProjectDocumentService projectDocumentService;
    private final PlanningAgentClient planningAgentClient;
    private final ProjectRequirementMapper projectRequirementMapper;
    private final ObjectMapper objectMapper;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final PlanningDocumentExtractionValidator extractionValidator;
    private final ProjectRequirementImportService requirementImportService;
    private final TransactionTemplate transactionTemplate;

    // Runs planning extraction synchronously and stores the validated requirements atomically.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectRequirementsResponse analyzeRequirements(
            Long projectId,
            List<Long> requestedDocumentIds
    ) {
        List<Long> documentIds = requestedDocumentIds.stream()
                .distinct()
                .sorted()
                .toList();
        String fingerprint = createFingerprint(projectId, documentIds);
        AnalysisPreparation preparation = inTransaction(
                () -> prepareAnalysis(projectId, documentIds, fingerprint)
        );
        List<StoredDocumentFile> files =
                projectDocumentService.getStoredDocumentFilesFromSnapshots(
                        preparation.documents()
                );
        PlanningDocumentExtractResponse response =
                planningAgentClient.extractDocuments(files, true);
        PlanningDocumentExtractionValidator.ValidatedResult validatedResult =
                extractionValidator.validateForRequirementAnalysis(
                        response,
                        preparation.documents().stream()
                                .map(ProjectDocumentService.StoredDocumentSnapshot::originalFileName)
                                .toList(),
                        preparation.projectName(),
                        preparation.projectDescription()
                );

        return inTransaction(() -> persistAnalysis(preparation, validatedResult));
    }

    private AnalysisPreparation prepareAnalysis(
            Long projectId,
            List<Long> documentIds,
            String fingerprint
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));
        List<ProjectDocument> documents =
                projectDocumentService.getAnalyzableProjectDocuments(
                        projectId,
                        documentIds
                );
        if (analysisResultRepository.existsByAgentExecutionId(fingerprint)) {
            throw duplicateAnalysis();
        }
        return new AnalysisPreparation(
                projectId,
                project.getName(),
                project.getDescription(),
                project.getUpdatedAt(),
                fingerprint,
                documentIds,
                documents.stream()
                        .map(ProjectDocumentService.StoredDocumentSnapshot::from)
                        .toList()
        );
    }

    private ProjectRequirementsResponse persistAnalysis(
            AnalysisPreparation preparation,
            PlanningDocumentExtractionValidator.ValidatedResult validatedResult
    ) {
        Project project = projectRepository.findForUpdate(preparation.projectId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found. projectId=" + preparation.projectId()
                ));
        projectAuthorizationService.requireProjectPm(project);
        if (!Objects.equals(project.getUpdatedAt(), preparation.projectUpdatedAt())
                || !Objects.equals(project.getName(), preparation.projectName())
                || !Objects.equals(project.getDescription(), preparation.projectDescription())) {
            throw analysisInputChanged();
        }

        List<ProjectDocument> documents =
                projectDocumentRepository.findForUpdate(
                        preparation.projectId(),
                        preparation.documentIds()
                );
        if (!documents.stream()
                .map(ProjectDocumentService.StoredDocumentSnapshot::from)
                .toList()
                .equals(preparation.documents())) {
            throw analysisInputChanged();
        }
        if (analysisResultRepository.existsByAgentExecutionId(preparation.fingerprint())) {
            throw duplicateAnalysis();
        }
        ProjectDocumentAnalysisResult analysisResult =
                reserveAnalysisResult(
                        project,
                        preparation.fingerprint(),
                        preparation.documentIds()
                );

        applyAnalysisResult(analysisResult, validatedResult.response().projectInfo());
        Map<String, ProjectDocument> documentByName = new LinkedHashMap<>();
        for (ProjectDocument document : documents) {
            PlanningDocumentExtractResponse.DocumentResult documentResult =
                    validatedResult.documentByName().get(document.getOriginalFileName());
            document.setStatus(ProjectDocumentStatus.ANALYZED);
            document.setFileType(documentResult.fileType().trim());
            document.setCharacterCount(documentResult.characterCount());
            document.setProcessingMode(documentResult.processingMode().trim());
            documentByName.put(document.getOriginalFileName(), document);
        }

        List<ProjectRequirement> requirements =
                requirementImportService.importRequirements(
                        project,
                        analysisResult,
                        documentByName,
                        validatedResult
                );
        projectRequirementRepository.flush();

        return new ProjectRequirementsResponse(
                preparation.projectId(),
                requirements.stream()
                        .map(projectRequirementMapper::toAiSuggestion)
                        .toList(),
                requirements.stream()
                        .map(projectRequirementMapper::toDetail)
                        .toList()
        );
    }

    // AI Server 분석 응답을 프로젝트 분석 결과와 요구사항으로 저장하고 결과 ID를 반환한다.
    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private ApiException analysisInputChanged() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROJECT_ANALYSIS_INPUT_CHANGED",
                "The project or selected documents changed during analysis."
        );
    }

    @Transactional
    public SaveDocumentAnalysisResultResponse saveAnalysisResult(Long projectId, SaveDocumentAnalysisResultRequest request) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        List<ProjectDocument> projectDocuments = projectDocumentRepository.findByProjectId(projectId);
        if (projectDocuments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }
        if (analysisResultRepository.existsByAgentExecutionId(request.agentExecutionId().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }

        Map<Long, ProjectDocument> documentById = new LinkedHashMap<>();
        for (ProjectDocument projectDocument : projectDocuments) {
            documentById.put(projectDocument.getId(), projectDocument);
        }

        ProjectDocumentAnalysisResult analysisResult = new ProjectDocumentAnalysisResult();
        analysisResult.setProject(project);
        analysisResult.setAgentExecutionId(request.agentExecutionId().trim());
        analysisResult.setAgentVersion(request.agentVersion().trim());
        analysisResult.setProjectGoal(request.projectGoal().trim());
        analysisResult.setScope(request.scope().trim());
        analysisResult.setDeliverablesJson(toArrayJson(request.deliverables()));
        analysisResult.setMilestonesJson(toArrayJson(request.milestones()));
        analysisResult.setTechnologyStacksJson(toArrayJson(request.technologyStacks()));
        analysisResult.setConstraintsJson(toArrayJson(request.constraints()));
        analysisResult.setRisksJson(toArrayJson(request.risks()));

        ProjectDocumentAnalysisResult savedAnalysisResult = analysisResultRepository.save(analysisResult);

        List<ProjectRequirement> requirements = new ArrayList<>();
        for (DocumentAnalysisRequirementRequest requirementRequest : request.requirements()) {
            ProjectDocument sourceDocument = documentById.get(requirementRequest.sourceDocumentId());
            if (sourceDocument == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "document not found");
            }

            ProjectRequirement requirement = new ProjectRequirement();
            requirement.setProject(project);
            requirement.setAnalysisResult(savedAnalysisResult);
            requirement.setSourceDocument(sourceDocument);
            requirement.setExternalReferenceId(requirementRequest.externalReferenceId());
            requirement.setType(parseRequirementType(requirementRequest.type()));
            requirement.setTitle(requirementRequest.title().trim());
            requirement.setDescription(requirementRequest.description().trim());
            requirement.setPriority(parseRequirementPriority(requirementRequest.priority()));
            requirement.setStatus(RequirementStatus.UNCONFIRMED);
            requirement.setConfirmed(false);
            requirement.setIncludedInFinal(true);
            projectRequirementMapper.captureAiSuggestion(requirement);
            requirements.add(requirement);
        }

        projectRequirementRepository.saveAll(requirements);
        for (ProjectDocument projectDocument : projectDocuments) {
            projectDocument.setStatus(ProjectDocumentStatus.ANALYZED);
        }

        return new SaveDocumentAnalysisResultResponse(
                savedAnalysisResult.getId(),
                project.getId(),
                savedAnalysisResult.getAgentExecutionId(),
                requirements.size()
        );
    }

    // 프로젝트의 문서와 최신 분석 연관 데이터를 조회해 화면용 통합 응답으로 반환한다.
    @Transactional(readOnly = true)
    public ProjectDocumentAnalysisResultsResponse getAnalysisResults(Long projectId) {
        Project project = projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "요청한 프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));
        projectAuthorizationService.requireProjectPm(projectId);

        List<ProjectDocument> documents = projectDocumentRepository.findByProjectIdOrderByCreatedAtAscIdAsc(projectId);
        if (documents.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROJECT_DOCUMENT_NOT_FOUND",
                    "프로젝트에 업로드된 문서가 없습니다. 문서를 먼저 업로드해 주세요. projectId=" + projectId
            );
        }

        ProjectDocumentAnalysisResult analysisResult = analysisResultRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_ANALYSIS_RESULT_NOT_FOUND",
                        "프로젝트에 저장된 AI 분석 결과가 없습니다. 문서 분석을 먼저 실행해 주세요. projectId=" + projectId
                ));

        List<ProjectRequirement> requirements = projectRequirementRepository
                .findByProjectIdAndAnalysisResultIdOrderByIdAsc(projectId, analysisResult.getId()).stream()
                .filter(ProjectRequirement::isIncludedInFinal)
                .toList();
        List<ProjectRequiredArtifact> requiredArtifacts = projectRequiredArtifactRepository
                .findByProjectIdOrderByIdAsc(projectId);
        List<ProjectKeyFeature> keyFeatures = projectKeyFeatureRepository
                .findByProjectIdOrderByIdAsc(projectId);
        ProjectPlanningExtraction planningExtraction = projectPlanningExtractionRepository
                .findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .orElse(null);

        return new ProjectDocumentAnalysisResultsResponse(
                toProjectDetail(project),
                documents.stream().map(this::toDocumentDetail).toList(),
                toAnalysisResultDetail(analysisResult),
                requirements.stream().map(projectRequirementMapper::toDetail).toList(),
                requiredArtifacts.stream().map(this::toRequiredArtifactDetail).toList(),
                keyFeatures.stream().map(this::toKeyFeatureDetail).toList(),
                toPlanningExtractionDetail(planningExtraction)
        );
    }

    private ProjectDocumentAnalysisResult reserveAnalysisResult(
            Project project,
            String fingerprint,
            List<Long> documentIds
    ) {
        ProjectDocumentAnalysisResult analysisResult = new ProjectDocumentAnalysisResult();
        analysisResult.setProject(project);
        analysisResult.setAgentExecutionId(fingerprint);
        analysisResult.setAgentVersion("planning-agent:" + ANALYSIS_CONTRACT_VERSION);
        analysisResult.setProjectGoal("Planning document analysis pending.");
        analysisResult.setScope("documentIds=" + documentIds);
        analysisResult.setDeliverablesJson("[]");
        analysisResult.setMilestonesJson("[]");
        analysisResult.setTechnologyStacksJson("[]");
        analysisResult.setConstraintsJson("[]");
        analysisResult.setRisksJson("[]");
        try {
            return analysisResultRepository.saveAndFlush(analysisResult);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateAnalysis(exception);
        }
    }

    private void applyAnalysisResult(
            ProjectDocumentAnalysisResult analysisResult,
            PlanningDocumentExtractResponse.ProjectInfo projectInfo
    ) {
        analysisResult.setProjectGoal(projectInfo.projectGoal().trim());
        analysisResult.setScope(buildScope(projectInfo));
        analysisResult.setDeliverablesJson(toStringListJson(
                projectInfo.requiredArtifacts().stream()
                        .map(PlanningDocumentExtractResponse.RequiredArtifact::artifactName)
                        .map(String::trim)
                        .toList()
        ));
        analysisResult.setMilestonesJson("[]");
        analysisResult.setTechnologyStacksJson("[]");
        analysisResult.setConstraintsJson(toStringListJson(defaultIfNull(
                projectInfo.budgetContractConditions()
        )));
        analysisResult.setRisksJson(toStringListJson(defaultIfNull(
                projectInfo.securityPrivacyConditions()
        )));
    }

    private String buildScope(PlanningDocumentExtractResponse.ProjectInfo projectInfo) {
        List<String> keyFeatures = defaultIfNull(projectInfo.keyFeatures()).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return keyFeatures.isEmpty()
                ? projectInfo.projectGoal().trim()
                : String.join(", ", keyFeatures);
    }

    private String toStringListJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_PLANNING_AGENT_RESPONSE",
                    "문서 분석 결과 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }

    private <T> List<T> defaultIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String createFingerprint(Long projectId, List<Long> documentIds) {
        String input = projectId
                + ":"
                + String.join(",", documentIds.stream().map(String::valueOf).toList())
                + ":"
                + ANALYSIS_CONTRACT_VERSION;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private ApiException duplicateAnalysis() {
        return duplicateAnalysis(null);
    }

    private ApiException duplicateAnalysis(Throwable cause) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROJECT_REQUIREMENT_ANALYSIS_DUPLICATE",
                "The selected project documents have already been analyzed.",
                cause
        );
    }

    // AI 요구사항 유형 문자열을 저장 가능한 RequirementType으로 변환한다.
    private RequirementType parseRequirementType(String value) {
        return parseEnum(value, RequirementType.class);
    }

    // AI 우선순위 문자열을 저장 가능한 RequirementPriority로 변환한다.
    private RequirementPriority parseRequirementPriority(String value) {
        return parseEnum(value, RequirementPriority.class);
    }

    // 문자열을 대소문자와 무관하게 지정 Enum으로 변환하고 잘못된 값은 요청 오류로 처리한다.
    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
    }

    // 요청 JSON 노드가 배열인지 검증해 DB 저장용 JSON 문자열로 반환한다.
    private String toArrayJson(JsonNode node) {
        JsonNode normalizedNode = node;
        if (normalizedNode == null || normalizedNode.isNull()) {
            normalizedNode = objectMapper.createArrayNode();
        }
        if (!normalizedNode.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid request");
        }

        try {
            return objectMapper.writeValueAsString(normalizedNode);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid request");
        }
    }

    // 프로젝트 엔티티를 PM 정보가 포함된 조회 응답 상세로 변환한다.
    private ProjectDocumentAnalysisResultsResponse.ProjectDetail toProjectDetail(Project project) {
        return new ProjectDocumentAnalysisResultsResponse.ProjectDetail(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getClientOrganization(),
                project.getPm().getEmployeeNumber(),
                project.getStatus(),
                project.getPlannedStartDate(),
                project.getPlannedEndDate(),
                project.getAcceptanceConditionsJson(),
                project.getBudgetContractConditionsJson(),
                project.getSecurityPrivacyConditionsJson(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    // 저장 문서 엔티티를 파일 메타데이터 조회 응답으로 변환한다.
    private ProjectDocumentAnalysisResultsResponse.DocumentDetail toDocumentDetail(ProjectDocument document) {
        return new ProjectDocumentAnalysisResultsResponse.DocumentDetail(
                document.getId(),
                document.getStatus(),
                document.getOriginalFileName(),
                document.getStoredFileName(),
                document.getStoragePath(),
                document.getExtension(),
                document.getContentType(),
                document.getFileSize(),
                document.getCharacterCount(),
                document.getFileType(),
                document.getProcessingMode(),
                document.getCreatedAt()
        );
    }

    // 최신 분석 결과 엔티티를 AI 분석 상세 응답으로 변환한다.
    private ProjectDocumentAnalysisResultsResponse.AnalysisResultDetail toAnalysisResultDetail(ProjectDocumentAnalysisResult analysisResult) {
        return new ProjectDocumentAnalysisResultsResponse.AnalysisResultDetail(
                analysisResult.getId(),
                analysisResult.getAgentExecutionId(),
                analysisResult.getAgentVersion(),
                analysisResult.getProjectGoal(),
                analysisResult.getScope(),
                analysisResult.getDeliverablesJson(),
                analysisResult.getMilestonesJson(),
                analysisResult.getTechnologyStacksJson(),
                analysisResult.getConstraintsJson(),
                analysisResult.getRisksJson(),
                analysisResult.getCreatedAt()
        );
    }

    // 필수 산출물 엔티티를 화면 표시용 산출물 상세로 변환한다.
    private ProjectDocumentAnalysisResultsResponse.RequiredArtifactDetail toRequiredArtifactDetail(ProjectRequiredArtifact requiredArtifact) {
        return new ProjectDocumentAnalysisResultsResponse.RequiredArtifactDetail(
                requiredArtifact.getId(),
                requiredArtifact.getArtifactType(),
                requiredArtifact.getArtifactName(),
                requiredArtifact.getRequiredVersion(),
                requiredArtifact.getCreatedAt(),
                requiredArtifact.getUpdatedAt()
        );
    }

    // 핵심 기능 엔티티를 화면 표시용 기능 상세로 변환한다.
    private ProjectDocumentAnalysisResultsResponse.KeyFeatureDetail toKeyFeatureDetail(ProjectKeyFeature keyFeature) {
        return new ProjectDocumentAnalysisResultsResponse.KeyFeatureDetail(
                keyFeature.getId(),
                keyFeature.getFeatureName(),
                keyFeature.getCreatedAt()
        );
    }

    // 최신 추출 요약 엔티티를 조회 응답으로 변환하며 값이 없으면 null을 반환한다.
    private ProjectDocumentAnalysisResultsResponse.PlanningExtractionDetail toPlanningExtractionDetail(ProjectPlanningExtraction planningExtraction) {
        if (planningExtraction == null) {
            return null;
        }

        return new ProjectDocumentAnalysisResultsResponse.PlanningExtractionDetail(
                planningExtraction.getId(),
                planningExtraction.getLlmStatus(),
                planningExtraction.getDocumentCount(),
                planningExtraction.getRequirementCount(),
                planningExtraction.getRequiredArtifactCount(),
                planningExtraction.getCreatedAt()
        );
    }

    private record AnalysisPreparation(
            Long projectId,
            String projectName,
            String projectDescription,
            LocalDateTime projectUpdatedAt,
            String fingerprint,
            List<Long> documentIds,
            List<ProjectDocumentService.StoredDocumentSnapshot> documents
    ) {
    }
}
