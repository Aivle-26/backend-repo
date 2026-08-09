package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithMockUser(username = "PM001", roles = "PM")
class ProjectDocumentAnalysisServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private PlanningAgentClient planningAgentClient;

    @Autowired
    private ProjectDocumentAnalysisService projectDocumentAnalysisService;

    @Autowired
    private ProjectDocumentService projectDocumentService;

    @Autowired
    private ProjectCreationService projectCreationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectRequiredArtifactRepository requiredArtifactRepository;

    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    private ProjectDocumentAnalysisResultRepository analysisResultRepository;

    @Autowired
    private ProjectRequirementRepository projectRequirementRepository;

    @Autowired
    private ProjectWbsTaskRepository projectWbsTaskRepository;

    @Autowired
    private ProjectWbsResultRepository projectWbsResultRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

    @Autowired
    private ProjectScheduleResultRepository projectScheduleResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        requiredArtifactRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(createPmUser("PM001"));
    }

    @Test
    void saveAnalysisResultSuccess() {
        ProjectDocument document = uploadDocument();

        SaveDocumentAnalysisResultResponse response = projectDocumentAnalysisService.saveAnalysisResult(
                document.getProject().getId(),
                new SaveDocumentAnalysisResultRequest(
                        "analysis-20260713-001",
                        "document-agent-v1",
                        "AI 기반 프로젝트 관리 플랫폼 구축",
                        "프로젝트 생성 및 관리 자동화",
                        List.of(new DocumentAnalysisRequirementRequest(
                                1L,
                                "FUNCTIONAL",
                                "프로젝트 문서 업로드",
                                "PM이 초기 문서를 업로드할 수 있다.",
                                "UNSPECIFIED",
                                document.getId()
                        )),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode()
                )
        );

        assertThat(response.analysisResultId()).isNotNull();
        assertThat(response.requirementsCount()).isEqualTo(1);
        ProjectDocumentAnalysisResult analysisResult = analysisResultRepository.findById(response.analysisResultId()).orElseThrow();
        assertThat(analysisResult.getAgentExecutionId()).isEqualTo("analysis-20260713-001");
        ProjectRequirement requirement = projectRequirementRepository.findAll().getFirst();
        assertThat(requirement.getPriority()).isEqualTo(RequirementPriority.UNSPECIFIED);
        assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.UNCONFIRMED);
        ProjectDocument refreshedDocument = projectDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(refreshedDocument.getStatus()).isEqualTo(ProjectDocumentStatus.ANALYZED);
    }

    @Test
    void saveAnalysisResultFailWhenAgentExecutionIdDuplicated() {
        ProjectDocument document = uploadDocument();
        SaveDocumentAnalysisResultRequest request = new SaveDocumentAnalysisResultRequest(
                "analysis-20260713-001",
                "document-agent-v1",
                "AI 기반 프로젝트 관리 플랫폼 구축",
                "프로젝트 생성 및 관리 자동화",
                List.of(new DocumentAnalysisRequirementRequest(
                        1L,
                        "FUNCTIONAL",
                        "프로젝트 문서 업로드",
                        "PM이 초기 문서를 업로드할 수 있다.",
                        "HIGH",
                        document.getId()
                )),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode()
        );

        projectDocumentAnalysisService.saveAnalysisResult(document.getProject().getId(), request);

        assertThatThrownBy(() -> projectDocumentAnalysisService.saveAnalysisResult(document.getProject().getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("duplicate agent execution id");
    }

    @Test
    void saveAnalysisResultFailWhenSourceDocumentIdInvalidAndRollback() {
        ProjectDocument document = uploadDocument();

        assertThatThrownBy(() -> projectDocumentAnalysisService.saveAnalysisResult(
                document.getProject().getId(),
                new SaveDocumentAnalysisResultRequest(
                        "analysis-20260713-001",
                        "document-agent-v1",
                        "AI 기반 프로젝트 관리 플랫폼 구축",
                        "프로젝트 생성 및 관리 자동화",
                        List.of(
                                new DocumentAnalysisRequirementRequest(
                                        1L,
                                        "FUNCTIONAL",
                                        "프로젝트 문서 업로드",
                                        "PM이 초기 문서를 업로드할 수 있다.",
                                        "HIGH",
                                        document.getId()
                                ),
                                new DocumentAnalysisRequirementRequest(
                                        2L,
                                        "FUNCTIONAL",
                                        "잘못된 문서 참조",
                                        "존재하지 않는 문서를 참조한다.",
                                        "HIGH",
                                        99999L
                                )
                        ),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode()
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("document not found");

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isZero();
        ProjectDocument refreshedDocument = projectDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(refreshedDocument.getStatus()).isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    @Test
    void analyzeRequirementsStoresUnconfirmedRequirementsAndSourceDocument() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(successResponse(List.of(document.getOriginalFileName())));

        ProjectRequirementsResponse response =
                projectDocumentAnalysisService.analyzeRequirements(
                        document.getProject().getId(),
                        List.of(document.getId())
                );

        assertThat(response.projectId()).isEqualTo(document.getProject().getId());
        assertThat(response.aiSuggestions()).hasSize(1);
        assertThat(response.finalRequirements()).hasSize(1);
        ProjectRequirement requirement = projectRequirementRepository.findAll().getFirst();
        assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.UNCONFIRMED);
        assertThat(requirement.isConfirmed()).isFalse();
        assertThat(requirement.getSourceDocument().getId()).isEqualTo(document.getId());
        assertThat(requirement.getExternalReferenceId()).isEqualTo(91001L);
        assertThat(requirement.getAiSuggestionJson()).isNotBlank();
        assertThat(analysisResultRepository.count()).isEqualTo(1);
        assertThat(projectDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectDocumentStatus.ANALYZED);
    }

    @Test
    void analyzeRequirementsPerformsStorageAndAiIoWithoutDbTransaction() {
        ProjectDocument document = uploadDocument();
        byte[] content = "transaction boundary".getBytes();
        AtomicBoolean storageObservedWithoutTransaction = new AtomicBoolean();
        AtomicBoolean aiObservedWithoutTransaction = new AtomicBoolean();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            storageObservedWithoutTransaction.set(
                    !TransactionSynchronizationManager.isActualTransactionActive()
            );
            return ResponseBytes.fromByteArray(
                    GetObjectResponse.builder()
                            .contentLength((long) content.length)
                            .build(),
                    content
            );
        });
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    aiObservedWithoutTransaction.set(
                            !TransactionSynchronizationManager.isActualTransactionActive()
                    );
                    return successResponse(List.of(document.getOriginalFileName()));
                });

        projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        );

        assertThat(storageObservedWithoutTransaction).isTrue();
        assertThat(aiObservedWithoutTransaction).isTrue();
    }

    @Test
    void storedDocumentDownloadSuspendsCallerTransactionAndDownloadsOnce() {
        ProjectDocument document = uploadDocument();
        byte[] content = "single download".getBytes();
        AtomicBoolean storageObservedWithoutTransaction = new AtomicBoolean();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            storageObservedWithoutTransaction.set(
                    !TransactionSynchronizationManager.isActualTransactionActive()
            );
            return ResponseBytes.fromByteArray(
                    GetObjectResponse.builder()
                            .contentLength((long) content.length)
                            .build(),
                    content
            );
        });

        List<StoredDocumentFile> files = transactionTemplate.execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            return projectDocumentService.getStoredDocumentFiles(
                    document.getProject().getId()
            );
        });

        assertThat(files).hasSize(1);
        assertThat(storageObservedWithoutTransaction).isTrue();
        verify(s3Client, times(1))
                .getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void pdfDownloadSuspendsCallerTransactionAndDownloadsOnce() {
        Long projectId = createProject("PM001");
        byte[] content = "%PDF-1.7".getBytes();
        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(new MockMultipartFile(
                        "files",
                        "evidence.pdf",
                        "application/pdf",
                        content
                ))
        );
        ProjectDocument document =
                projectDocumentRepository.findByProjectId(projectId).getFirst();
        AtomicBoolean storageObservedWithoutTransaction = new AtomicBoolean();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            storageObservedWithoutTransaction.set(
                    !TransactionSynchronizationManager.isActualTransactionActive()
            );
            return ResponseBytes.fromByteArray(
                    GetObjectResponse.builder()
                            .contentLength((long) content.length)
                            .build(),
                    content
            );
        });

        ProjectDocumentService.ProjectDocumentContent result =
                transactionTemplate.execute(status -> {
                    assertThat(TransactionSynchronizationManager
                            .isActualTransactionActive()).isTrue();
                    return projectDocumentService.getPdfContent(
                            projectId,
                            document.getId()
                    );
                });

        assertThat(result.content()).isEqualTo(content);
        assertThat(storageObservedWithoutTransaction).isTrue();
        verify(s3Client, times(1))
                .getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void analyzeRequirementsTimeoutLeavesNoPartialData() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenThrow(new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "PLANNING_AGENT_TIMEOUT",
                        "Document analysis timed out."
                ));

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus())
                                    .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                            assertThat(exception.getCode())
                                    .isEqualTo("PLANNING_AGENT_TIMEOUT");
                        }
                );

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isZero();
        assertThat(projectDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    @Test
    void analyzeRequirementsRejectsDocumentChangedDuringAiCall() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    ProjectDocument changed = projectDocumentRepository
                            .findById(document.getId())
                            .orElseThrow();
                    changed.setStoragePath(changed.getStoragePath() + "-replaced");
                    projectDocumentRepository.saveAndFlush(changed);
                    return successResponse(List.of(document.getOriginalFileName()));
                });

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getCode())
                                    .isEqualTo("PROJECT_ANALYSIS_INPUT_CHANGED");
                        }
                );

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isZero();
    }

    @Test
    void analyzeRequirementsRollsBackAllWritesWhenPersistenceFails() {
        ProjectDocument document = uploadDocument();
        ProjectRequirement existing = new ProjectRequirement();
        existing.setProject(projectRepository.findById(document.getProject().getId()).orElseThrow());
        existing.setSourceDocument(document);
        existing.setExternalReferenceId(91001L);
        existing.setType(RequirementType.FUNCTIONAL);
        existing.setTitle("Existing");
        existing.setDescription("Existing requirement");
        existing.setPriority(RequirementPriority.HIGH);
        existing.setStatus(RequirementStatus.UNCONFIRMED);
        existing.setConfirmed(false);
        existing.setIncludedInFinal(true);
        projectRequirementRepository.saveAndFlush(existing);
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(successResponse(List.of(document.getOriginalFileName())));

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        )).isInstanceOf(RuntimeException.class);

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isEqualTo(1);
        assertThat(projectDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    @Test
    void analyzeRequirementsPersistsValidatedEvidence() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        PlanningDocumentExtractResponse base =
                successResponse(List.of(document.getOriginalFileName()));
        PlanningDocumentExtractResponse.RequirementCandidate source =
                base.requirementCandidates().getFirst();
        PlanningDocumentExtractResponse response =
                new PlanningDocumentExtractResponse(
                        base.projectInfo(),
                        List.of(new PlanningDocumentExtractResponse.RequirementCandidate(
                                source.requirementId(),
                                source.functionName(),
                                source.requirementText(),
                                source.category(),
                                source.priority(),
                                source.acceptanceCriteria(),
                                source.dueDate(),
                                source.deliverableName(),
                                source.securityCondition(),
                                source.sourceDocument(),
                                "User login",
                                List.of(new PlanningDocumentExtractResponse.RequirementEvidence(
                                        document.getId(),
                                        document.getOriginalFileName(),
                                        2,
                                        document.getId() + ":2:1",
                                        "User login",
                                        10,
                                        20,
                                        List.of()
                                ))
                        )),
                        base.documents(),
                        base.llmStatus()
                );
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(response);

        ProjectRequirementsResponse result =
                projectDocumentAnalysisService.analyzeRequirements(
                        document.getProject().getId(),
                        List.of(document.getId())
                );

        assertThat(result.finalRequirements().getFirst().evidences()).hasSize(1);
        var evidence = result.finalRequirements().getFirst().evidences().getFirst();
        assertThat(evidence.documentId()).isEqualTo(document.getId());
        assertThat(evidence.pageNumber()).isEqualTo(2);
        assertThat(evidence.chunkId()).isEqualTo(document.getId() + ":2:1");
        assertThat(evidence.quoteText()).isEqualTo("User login");
    }

    @Test
    void analyzeRequirementsRejectsEvidenceFromAnotherDocument() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        PlanningDocumentExtractResponse base =
                successResponse(List.of(document.getOriginalFileName()));
        PlanningDocumentExtractResponse.RequirementCandidate source =
                base.requirementCandidates().getFirst();
        PlanningDocumentExtractResponse response =
                new PlanningDocumentExtractResponse(
                        base.projectInfo(),
                        List.of(new PlanningDocumentExtractResponse.RequirementCandidate(
                                source.requirementId(),
                                source.functionName(),
                                source.requirementText(),
                                source.category(),
                                source.priority(),
                                source.acceptanceCriteria(),
                                source.dueDate(),
                                source.deliverableName(),
                                source.securityCondition(),
                                source.sourceDocument(),
                                source.sourceExcerpt(),
                                List.of(new PlanningDocumentExtractResponse.RequirementEvidence(
                                        999999L,
                                        document.getOriginalFileName(),
                                        1,
                                        "999999:1:1",
                                        "User login",
                                        0,
                                        10,
                                        List.of()
                                ))
                        )),
                        base.documents(),
                        base.llmStatus()
                );
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(response);

        assertThatThrownBy(() ->
                projectDocumentAnalysisService.analyzeRequirements(
                        document.getProject().getId(),
                        List.of(document.getId())
                ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("evidence document");
        assertThat(projectRequirementRepository.count()).isZero();
        assertThat(analysisResultRepository.count()).isZero();
    }

    @Test
    void analyzeRequirementsAcceptsOptionalProjectMetadataFromAiContract() throws Exception {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        PlanningDocumentExtractResponse response;
        try (var fixture = getClass().getResourceAsStream(
                "/fixtures/planning/optional-project-metadata-response.json"
        )) {
            assertThat(fixture).isNotNull();
            response = objectMapper.readValue(
                    fixture,
                    PlanningDocumentExtractResponse.class
            );
        }
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(response);

        ProjectRequirementsResponse result =
                projectDocumentAnalysisService.analyzeRequirements(
                        document.getProject().getId(),
                        List.of(document.getId())
                );

        assertThat(result.finalRequirements()).hasSize(1);
        ProjectDocumentAnalysisResult analysisResult =
                analysisResultRepository.findAll().getFirst();
        assertThat(analysisResult.getProjectGoal()).isEqualTo("draft description");
        ProjectRequirement requirement = projectRequirementRepository.findAll().getFirst();
        assertThat(requirement.getSourceDocument().getId()).isEqualTo(document.getId());
    }

    @Test
    void analyzeRequirementsRollsBackInvalidRequirementEnum() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        PlanningDocumentExtractResponse valid =
                successResponse(List.of(document.getOriginalFileName()));
        PlanningDocumentExtractResponse.RequirementCandidate requirement =
                valid.requirementCandidates().getFirst();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(new PlanningDocumentExtractResponse(
                        valid.projectInfo(),
                        List.of(new PlanningDocumentExtractResponse.RequirementCandidate(
                                requirement.requirementId(),
                                requirement.functionName(),
                                requirement.requirementText(),
                                "UNKNOWN",
                                requirement.priority(),
                                requirement.acceptanceCriteria(),
                                requirement.dueDate(),
                                requirement.deliverableName(),
                                requirement.securityCondition(),
                                requirement.sourceDocument(),
                                requirement.sourceExcerpt()
                        )),
                        valid.documents(),
                        valid.llmStatus()
                ));

        assertInvalidAnalysisRollsBack(document);
    }

    @Test
    void analyzeRequirementsRollsBackMissingRequiredRequirementText() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        PlanningDocumentExtractResponse valid =
                successResponse(List.of(document.getOriginalFileName()));
        PlanningDocumentExtractResponse.RequirementCandidate requirement =
                valid.requirementCandidates().getFirst();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(new PlanningDocumentExtractResponse(
                        valid.projectInfo(),
                        List.of(new PlanningDocumentExtractResponse.RequirementCandidate(
                                requirement.requirementId(),
                                requirement.functionName(),
                                null,
                                requirement.category(),
                                requirement.priority(),
                                requirement.acceptanceCriteria(),
                                requirement.dueDate(),
                                requirement.deliverableName(),
                                requirement.securityCondition(),
                                requirement.sourceDocument(),
                                requirement.sourceExcerpt()
                        )),
                        valid.documents(),
                        valid.llmStatus()
                ));

        assertInvalidAnalysisRollsBack(document);
    }

    @Test
    void analyzeRequirementsMarksOnlySelectedDocumentsAsAnalyzed() {
        Long projectId = createProject("PM001");
        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(
                        new MockMultipartFile(
                                "files",
                                "selected.txt",
                                "text/plain",
                                "selected".getBytes()
                        ),
                        new MockMultipartFile(
                                "files",
                                "not-selected.txt",
                                "text/plain",
                                "not selected".getBytes()
                        )
                )
        );
        List<ProjectDocument> documents =
                projectDocumentRepository.findByProjectIdOrderByCreatedAtAscIdAsc(projectId);
        ProjectDocument selectedDocument = documents.stream()
                .filter(document -> "selected.txt".equals(document.getOriginalFileName()))
                .findFirst()
                .orElseThrow();
        ProjectDocument notSelectedDocument = documents.stream()
                .filter(document -> "not-selected.txt".equals(document.getOriginalFileName()))
                .findFirst()
                .orElseThrow();
        notSelectedDocument.setStatus(ProjectDocumentStatus.ANALYZED);
        projectDocumentRepository.saveAndFlush(notSelectedDocument);
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(successResponse(List.of(selectedDocument.getOriginalFileName())));

        projectDocumentAnalysisService.analyzeRequirements(
                projectId,
                List.of(selectedDocument.getId())
        );

        assertThat(projectDocumentRepository.findById(selectedDocument.getId())
                .orElseThrow()
                .getStatus()).isEqualTo(ProjectDocumentStatus.ANALYZED);
        assertThat(projectDocumentRepository.findById(notSelectedDocument.getId())
                .orElseThrow()
                .getStatus()).isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    @Test
    void analyzeRequirementsSortsAndDeduplicatesDocumentIds() {
        Long projectId = createProject("PM001");
        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(
                        new MockMultipartFile(
                                "files",
                                "first.txt",
                                "text/plain",
                                "first".getBytes()
                        ),
                        new MockMultipartFile(
                                "files",
                                "second.txt",
                                "text/plain",
                                "second".getBytes()
                        )
                )
        );
        List<ProjectDocument> documents =
                projectDocumentRepository.findByProjectIdOrderByCreatedAtAscIdAsc(projectId);
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(successResponse(
                        documents.stream().map(ProjectDocument::getOriginalFileName).toList()
                ));

        projectDocumentAnalysisService.analyzeRequirements(
                projectId,
                List.of(
                        documents.get(1).getId(),
                        documents.get(0).getId(),
                        documents.get(1).getId()
                )
        );

        ArgumentCaptor<List<StoredDocumentFile>> filesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(planningAgentClient).extractDocuments(filesCaptor.capture(), anyBoolean());
        assertThat(filesCaptor.getValue())
                .extracting(StoredDocumentFile::originalFileName)
                .containsExactly("first.txt", "second.txt");
    }

    @Test
    void analyzeRequirementsRejectsMixedProjectDocumentIds() {
        ProjectDocument firstProjectDocument = uploadDocument();
        Long otherProjectId = createProject("PM001");
        projectDocumentService.uploadInitialDocuments(
                otherProjectId,
                List.of(new MockMultipartFile(
                        "files",
                        "other.txt",
                        "text/plain",
                        "other".getBytes()
                ))
        );
        ProjectDocument otherProjectDocument =
                projectDocumentRepository.findByProjectId(otherProjectId).getFirst();

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                firstProjectDocument.getProject().getId(),
                List.of(firstProjectDocument.getId(), otherProjectDocument.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(exception.getCode())
                                    .isEqualTo("INVALID_PROJECT_DOCUMENT_SELECTION");
                        }
                );

        verify(planningAgentClient, times(0)).extractDocuments(any(), anyBoolean());
        assertThat(analysisResultRepository.count()).isZero();
    }

    @Test
    void analyzeRequirementsRejectsEmptyDocumentIds() {
        Long projectId = createProject("PM001");

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                projectId,
                List.of()
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.getStatus())
                                .isEqualTo(HttpStatus.BAD_REQUEST)
                );
    }

    @Test
    void analyzeRequirementsRejectsDuplicateFingerprint() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenReturn(successResponse(List.of(document.getOriginalFileName())));

        projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        );

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getCode())
                                    .isEqualTo("PROJECT_REQUIREMENT_ANALYSIS_DUPLICATE");
                        }
                );
        verify(planningAgentClient).extractDocuments(any(), anyBoolean());
        assertThat(projectRequirementRepository.count()).isEqualTo(1);
    }

    @Test
    void analyzeRequirementsRollsBackInvalidAgentResponse() {
        ProjectDocument document = uploadDocument();
        prepareStoredContent();
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                            assertThat(exception.getCode())
                                    .isEqualTo("INVALID_PLANNING_AGENT_RESPONSE");
                        }
                );

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isZero();
        assertThat(projectDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    private void assertInvalidAnalysisRollsBack(ProjectDocument document) {
        assertThatThrownBy(() -> projectDocumentAnalysisService.analyzeRequirements(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                            assertThat(exception.getCode())
                                    .isEqualTo("INVALID_PLANNING_AGENT_RESPONSE");
                        }
                );

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isZero();
        assertThat(projectDocumentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    private ProjectDocument uploadDocument() {
        Long projectId = createProject("PM001");

        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes()))
        );

        return projectDocumentRepository.findByProjectId(projectId).getFirst();
    }

    private Long createProject(String employeeNumber) {
        return projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project",
                "draft description",
                null,
                employeeNumber,
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        )).projectId();
    }

    private void prepareStoredContent() {
        byte[] content = "E2E source content".getBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder()
                                .contentLength((long) content.length)
                                .build(),
                        content
                )
        );
    }

    private PlanningDocumentExtractResponse successResponse(List<String> fileNames) {
        String sourceFileName = fileNames.getFirst();
        return new PlanningDocumentExtractResponse(
                new PlanningDocumentExtractResponse.ProjectInfo(
                        "E2E Project",
                        "Validate requirement analysis.",
                        "AIPM",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31),
                        List.of("Login", "Document Upload"),
                        List.of(new PlanningDocumentExtractResponse.RequiredArtifact(
                                "REQUIREMENTS_DEFINITION",
                                "Requirements Definition",
                                "1.0"
                        )),
                        List.of("Requirements are persisted."),
                        List.of(),
                        List.of()
                ),
                List.of(new PlanningDocumentExtractResponse.RequirementCandidate(
                        91001L,
                        "User Login",
                        "A registered user can sign in.",
                        "FUNCTIONAL",
                        "HIGH",
                        "A session is issued.",
                        null,
                        "Requirements Definition",
                        null,
                        sourceFileName,
                        "User login"
                )),
                fileNames.stream()
                        .map(fileName -> new PlanningDocumentExtractResponse.DocumentResult(
                                fileName,
                                "TXT",
                                18L,
                                "TEXT"
                        ))
                        .toList(),
                "SUCCEEDED"
        );
    }

    private User createPmUser(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Project Manager");
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setPassword("encoded-password");
        user.setRole("PM");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }
}
