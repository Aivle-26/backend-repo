package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.ProjectKeyFeature;
import com.aivle26.aipm.Entity.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.ProjectRequirement;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectDocumentAnalysisQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository projectRequiredArtifactRepository;
    private final ProjectKeyFeatureRepository projectKeyFeatureRepository;
    private final ProjectPlanningExtractionRepository projectPlanningExtractionRepository;

    @Transactional(readOnly = true)
    public ProjectDocumentAnalysisResultsResponse getAnalysisResults(Long projectId) {
        Project project = projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "요청한 프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));

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
                .findByProjectIdAndAnalysisResultIdOrderByIdAsc(projectId, analysisResult.getId());
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
                requirements.stream().map(this::toRequirementDetail).toList(),
                requiredArtifacts.stream().map(this::toRequiredArtifactDetail).toList(),
                keyFeatures.stream().map(this::toKeyFeatureDetail).toList(),
                toPlanningExtractionDetail(planningExtraction)
        );
    }

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

    private ProjectDocumentAnalysisResultsResponse.RequirementDetail toRequirementDetail(ProjectRequirement requirement) {
        return new ProjectDocumentAnalysisResultsResponse.RequirementDetail(
                requirement.getId(),
                requirement.getAnalysisResult() == null ? null : requirement.getAnalysisResult().getId(),
                requirement.getSourceDocument() == null ? null : requirement.getSourceDocument().getId(),
                requirement.getExternalReferenceId(),
                requirement.getType(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getAcceptanceCriteria(),
                requirement.getDueDate(),
                requirement.getDeliverableName(),
                requirement.getSecurityCondition(),
                requirement.getSourceDocumentName(),
                requirement.getSourceExcerpt(),
                requirement.getPriority(),
                requirement.getStatus(),
                requirement.isConfirmed(),
                requirement.getCreatedAt(),
                requirement.getUpdatedAt()
        );
    }

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

    private ProjectDocumentAnalysisResultsResponse.KeyFeatureDetail toKeyFeatureDetail(ProjectKeyFeature keyFeature) {
        return new ProjectDocumentAnalysisResultsResponse.KeyFeatureDetail(
                keyFeature.getId(),
                keyFeature.getFeatureName(),
                keyFeature.getCreatedAt()
        );
    }

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
