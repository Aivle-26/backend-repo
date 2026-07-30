package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Mapper.project.ProjectRequirementMapper;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentAnalysisConcurrencyTest {
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private ProjectDocumentAnalysisResultRepository analysisResultRepository;
    @Mock
    private ProjectRequirementRepository projectRequirementRepository;
    @Mock
    private ProjectRequiredArtifactRepository requiredArtifactRepository;
    @Mock
    private ProjectKeyFeatureRepository keyFeatureRepository;
    @Mock
    private ProjectPlanningExtractionRepository extractionRepository;
    @Mock
    private ProjectDocumentService projectDocumentService;
    @Mock
    private PlanningAgentClient planningAgentClient;
    @Mock
    private ProjectRequirementMapper requirementMapper;
    @Mock
    private ProjectAuthorizationService authorizationService;
    @Mock
    private PlanningDocumentExtractionValidator extractionValidator;
    @Mock
    private ProjectRequirementImportService requirementImportService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    void uniqueFingerprintConflictIsMappedToConflict() {
        Long projectId = 42L;
        Long documentId = 7L;
        Project project = new Project();
        project.setId(projectId);
        ProjectDocument document = new ProjectDocument();
        document.setId(documentId);
        document.setProject(project);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.findForUpdate(projectId)).thenReturn(Optional.of(project));
        when(projectDocumentService.getAnalyzableProjectDocuments(
                projectId,
                List.of(documentId)
        )).thenReturn(List.of(document));
        when(projectDocumentRepository.findForUpdate(
                projectId,
                List.of(documentId)
        )).thenReturn(List.of(document));
        when(projectDocumentService.getStoredDocumentFilesFromSnapshots(any()))
                .thenReturn(List.of());
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(mock(PlanningDocumentExtractResponse.class));
        when(extractionValidator.validateForRequirementAnalysis(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(mock(PlanningDocumentExtractionValidator.ValidatedResult.class));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(
                    0,
                    org.springframework.transaction.support.TransactionCallback.class
            );
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(analysisResultRepository.existsByAgentExecutionId(any())).thenReturn(false);
        when(analysisResultRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique conflict"));

        ProjectDocumentAnalysisService service = new ProjectDocumentAnalysisService(
                projectRepository,
                projectDocumentRepository,
                analysisResultRepository,
                projectRequirementRepository,
                requiredArtifactRepository,
                keyFeatureRepository,
                extractionRepository,
                projectDocumentService,
                planningAgentClient,
                requirementMapper,
                new ObjectMapper(),
                authorizationService,
                extractionValidator,
                requirementImportService,
                transactionTemplate
        );

        assertThatThrownBy(() -> service.analyzeRequirements(
                projectId,
                List.of(documentId)
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getCode())
                                    .isEqualTo("PROJECT_REQUIREMENT_ANALYSIS_DUPLICATE");
                        }
                );
        verify(projectRepository).findForUpdate(projectId);
        verify(projectDocumentRepository).findForUpdate(
                projectId,
                List.of(documentId)
        );
    }
}
