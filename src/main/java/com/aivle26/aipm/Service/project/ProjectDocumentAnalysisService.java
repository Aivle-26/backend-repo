package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.ProjectAgentClient;
import com.aivle26.aipm.Dto.project.AgentRequestResult;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectDocumentAnalysisService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository projectRequiredArtifactRepository;
    private final ProjectKeyFeatureRepository projectKeyFeatureRepository;
    private final ProjectPlanningExtractionRepository projectPlanningExtractionRepository;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectAgentClient projectAgentClient;
    private final ProjectRequirementMapper projectRequirementMapper;
    private final ObjectMapper objectMapper;
    private final ProjectAuthorizationService projectAuthorizationService;

    // 프로젝트와 저장 파일 존재를 검증한 뒤 AI Server에 문서 분석을 요청한다.
    @Transactional(readOnly = true)
    public AgentRequestResult requestAnalysis(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "project not found");
        }
        projectDocumentService.getStoredDocumentFiles(projectId);
        return projectAgentClient.requestDocumentAnalysis(projectId);
    }

    // AI Server 분석 응답을 프로젝트 분석 결과와 요구사항으로 저장하고 결과 ID를 반환한다.
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
}
