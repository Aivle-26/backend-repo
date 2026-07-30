package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Dto.project.PlanningRequirementReadjustResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.RequirementChangeProposal;
import com.aivle26.aipm.Dto.project.RequirementReadjustmentResponse;
import com.aivle26.aipm.Dto.project.ReviewRequirementChangeRequest;
import com.aivle26.aipm.Dto.project.SaveFinalRequirementsRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectRequirementChangeCandidate;
import com.aivle26.aipm.Entity.project.ProjectRequirementEvidence;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.RequirementChangeReviewStatus;
import com.aivle26.aipm.Entity.project.RequirementChangeType;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementChangeCandidateRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithMockUser(username = "PM-EVIDENCE", roles = "PM")
class ProjectRequirementReadjustmentServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private PlanningAgentClient planningAgentClient;

    @Autowired
    private ProjectRequirementReadjustmentService service;

    @Autowired
    private ProjectRequirementService requirementService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private ProjectRepository projectRepository;

    @MockitoSpyBean
    private ProjectDocumentRepository documentRepository;

    @MockitoSpyBean
    private ProjectRequirementRepository requirementRepository;

    @Autowired
    private ProjectRequirementChangeCandidateRepository candidateRepository;

    @Autowired
    private ProjectDocumentAnalysisResultRepository analysisResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        candidateRepository.deleteAll();
        requirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        documentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void approvedAddedCandidateIsAppliedExactlyOnce() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        byte[] content = "new audit requirement".getBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder()
                                .contentLength((long) content.length)
                                .build(),
                        content
                )
        );
        PlanningDocumentExtractResponse.RequirementEvidence evidence =
                new PlanningDocumentExtractResponse.RequirementEvidence(
                        document.getId(),
                        document.getOriginalFileName(),
                        3,
                        document.getId() + ":3:1",
                        "The system must retain audit logs.",
                        0,
                        34,
                        List.of()
                );
        PlanningDocumentExtractResponse.RequirementCandidate proposal =
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        1L,
                        "Audit log",
                        "The system must retain audit logs.",
                        "SECURITY",
                        "HIGH",
                        null,
                        null,
                        null,
                        "Audit records must be protected.",
                        document.getOriginalFileName(),
                        evidence.quoteText(),
                        List.of(evidence)
                );
        PlanningDocumentExtractResponse.RequirementCandidate rejectedProposal =
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        2L,
                        "Session retention",
                        "The system must retain sessions for seven days.",
                        "SECURITY",
                        "MEDIUM",
                        null,
                        null,
                        null,
                        null,
                        document.getOriginalFileName(),
                        evidence.quoteText(),
                        List.of(evidence)
                );
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenReturn(new PlanningRequirementReadjustResponse(
                        List.of(
                                new PlanningRequirementReadjustResponse.ChangeCandidate(
                                        "CHG-001",
                                        null,
                                        "ADDED",
                                        "A new requirement was found.",
                                        null,
                                        proposal,
                                        List.of(evidence),
                                        "PENDING_REVIEW"
                                ),
                                new PlanningRequirementReadjustResponse.ChangeCandidate(
                                        "CHG-002",
                                        null,
                                        "ADDED",
                                        "A second requirement was found.",
                                        null,
                                        rejectedProposal,
                                        List.of(evidence),
                                        "PENDING_REVIEW"
                                )
                        ),
                        List.of(new PlanningDocumentExtractResponse.DocumentResult(
                                document.getOriginalFileName(),
                                "TXT",
                                21L,
                                "TEXT"
                        )),
                        "SUCCEEDED"
                ));

        RequirementReadjustmentResponse readjustment =
                service.createCandidates(
                        document.getProject().getId(),
                        List.of(document.getId())
                );

        assertThat(requirementRepository.findByProjectIdOrderByIdAsc(
                document.getProject().getId()
        )).hasSize(1);
        assertThat(readjustment.changeCandidates()).hasSize(2);
        assertThat(readjustment.changeCandidates().getFirst().reviewStatus())
                .isEqualTo(RequirementChangeReviewStatus.PENDING_REVIEW);
        assertThat(readjustment.changeCandidates().getFirst().evidences())
                .hasSize(1);

        Long candidateId =
                readjustment.changeCandidates().getFirst().candidateId();
        Long rejectedCandidateId =
                readjustment.changeCandidates().get(1).candidateId();
        service.review(
                document.getProject().getId(),
                candidateId,
                new ReviewRequirementChangeRequest(
                        RequirementChangeReviewStatus.APPROVED,
                        null
                )
        );
        service.review(
                document.getProject().getId(),
                rejectedCandidateId,
                new ReviewRequirementChangeRequest(
                        RequirementChangeReviewStatus.REJECTED,
                        null
                )
        );
        ProjectRequirementsResponse firstApply = service.apply(
                document.getProject().getId(),
                List.of(candidateId)
        );
        ProjectRequirementsResponse secondApply = service.apply(
                document.getProject().getId(),
                List.of(candidateId)
        );

        assertThat(firstApply.finalRequirements()).hasSize(2);
        assertThat(secondApply.finalRequirements()).hasSize(2);
        assertThat(
                secondApply.finalRequirements().stream()
                        .filter(requirement -> "Audit log".equals(requirement.title()))
                        .findFirst()
                        .orElseThrow()
                        .evidences()
        ).hasSize(1);
        assertThat(requirementRepository.findByProjectIdOrderByIdAsc(
                document.getProject().getId()
        )).hasSize(2);
        assertThat(service.listCandidates(document.getProject().getId())
                .changeCandidates())
                .satisfiesExactly(
                        applied -> assertThat(applied.applied()).isTrue(),
                        rejected -> {
                            assertThat(rejected.applied()).isFalse();
                            assertThat(rejected.reviewStatus())
                                    .isEqualTo(RequirementChangeReviewStatus.REJECTED);
                        }
                );
    }

    @Test
    void createCandidatesRejectsNullChangeCandidates() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        prepareStoredContent();
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenReturn(new PlanningRequirementReadjustResponse(
                        null,
                        List.of(),
                        "SUCCEEDED"
                ));

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus().value()).isEqualTo(502);
                            assertThat(exception.getCode())
                                    .isEqualTo("INVALID_PLANNING_AGENT_RESPONSE");
                        }
                );
        assertThat(candidateRepository.count()).isZero();
    }

    @Test
    void createCandidatesRejectsNullEvidenceElementInProposedRequirement() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        prepareStoredContent();
        PlanningDocumentExtractResponse.RequirementCandidate proposal =
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        2L,
                        "Invalid evidence",
                        "The proposal contains an invalid evidence element.",
                        "FUNCTIONAL",
                        "MEDIUM",
                        null,
                        null,
                        null,
                        null,
                        document.getOriginalFileName(),
                        null,
                        Collections.singletonList(null)
                );
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenReturn(new PlanningRequirementReadjustResponse(
                        List.of(new PlanningRequirementReadjustResponse.ChangeCandidate(
                                "CHG-NULL-EVIDENCE",
                                null,
                                "ADDED",
                                "Invalid evidence response",
                                null,
                                proposal,
                                List.of(),
                                "PENDING_REVIEW"
                        )),
                        List.of(),
                        "SUCCEEDED"
                ));

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus().value()).isEqualTo(502);
                            assertThat(exception.getCode())
                                    .isEqualTo("INVALID_PLANNING_AGENT_RESPONSE");
                        }
                );
        assertThat(candidateRepository.count()).isZero();
    }

    @Test
    void createCandidatesAcceptsEmptyChangeCandidatesAndRunsIoWithoutTransaction() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        byte[] content = "no requirement changes".getBytes();
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
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenAnswer(invocation -> {
                    aiObservedWithoutTransaction.set(
                            !TransactionSynchronizationManager.isActualTransactionActive()
                    );
                    return emptyReadjustmentResponse();
                });

        RequirementReadjustmentResponse response = service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        );

        assertThat(response.changeCandidates()).isEmpty();
        assertThat(candidateRepository.count()).isZero();
        assertThat(storageObservedWithoutTransaction).isTrue();
        assertThat(aiObservedWithoutTransaction).isTrue();
        verify(projectRepository).findForUpdate(document.getProject().getId());
        verify(documentRepository).findForUpdate(
                document.getProject().getId(),
                List.of(document.getId())
        );
        verify(requirementRepository)
                .findFinalForUpdate(document.getProject().getId());
    }

    @Test
    void createCandidatesTimeoutLeavesNoPartialCandidates() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        prepareStoredContent();
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenThrow(new ApiException(
                        org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                        "PLANNING_AGENT_TIMEOUT",
                        "Requirement readjustment timed out."
                ));

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus().value()).isEqualTo(504);
                            assertThat(exception.getCode())
                                    .isEqualTo("PLANNING_AGENT_TIMEOUT");
                        }
                );
        assertThat(candidateRepository.count()).isZero();
        assertThat(requirementRepository.count()).isEqualTo(1);
    }

    @Test
    void createCandidatesRejectsRequirementChangedDuringAiCall() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        prepareStoredContent();
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenAnswer(invocation -> {
                    ProjectRequirement changed = requirementRepository
                            .findByProjectIdOrderByIdAsc(document.getProject().getId())
                            .getFirst();
                    changed.setDescription("Changed while AI was running.");
                    requirementRepository.saveAndFlush(changed);
                    return emptyReadjustmentResponse();
                });

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus().value()).isEqualTo(409);
                            assertThat(exception.getCode())
                                    .isEqualTo("PROJECT_READJUSTMENT_INPUT_CHANGED");
                        }
                );
        assertThat(candidateRepository.count()).isZero();
    }

    @Test
    void createCandidatesRejectsEvidenceOnlyChangeDuringAiCall() {
        ProjectDocument document = createProjectDocument();
        ProjectRequirement requirement = createExistingRequirement(document);
        ProjectRequirementEvidence evidence = new ProjectRequirementEvidence();
        evidence.setDocument(document);
        evidence.setPageNumber(1);
        evidence.setChunkId(document.getId() + ":1:1");
        evidence.setQuoteText("Original evidence");
        evidence.setStartOffset(0);
        evidence.setEndOffset(17);
        evidence.setBoundingBoxesJson("[]");
        requirement.addEvidence(evidence);
        requirementRepository.saveAndFlush(requirement);
        LocalDateTime requirementUpdatedAt =
                requirementRepository.findById(requirement.getId())
                        .orElseThrow()
                        .getUpdatedAt();
        prepareStoredContent();
        when(planningAgentClient.readjustRequirements(any(), any()))
                .thenAnswer(invocation -> {
                    ProjectRequirement changed = requirementRepository
                            .findByProjectIdOrderByIdAsc(document.getProject().getId())
                            .getFirst();
                    changed.getEvidences().getFirst()
                            .setQuoteText("Changed evidence only");
                    requirementRepository.saveAndFlush(changed);
                    return emptyReadjustmentResponse();
                });

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        ))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.getStatus().value()).isEqualTo(409);
                            assertThat(exception.getCode())
                                    .isEqualTo("PROJECT_READJUSTMENT_INPUT_CHANGED");
                        }
                );

        ProjectRequirement persisted =
                requirementRepository.findByProjectIdOrderByIdAsc(
                        document.getProject().getId()
                ).getFirst();
        assertThat(persisted.getUpdatedAt()).isEqualTo(requirementUpdatedAt);
        assertThat(persisted.getEvidences().getFirst().getQuoteText())
                .isEqualTo("Changed evidence only");
        assertThat(candidateRepository.count()).isZero();
    }

    @Test
    void saveFinalAllowsExternalReferenceIdSwap() {
        ProjectDocument document = createProjectDocument();
        ProjectRequirement first =
                createExistingRequirement(document, 1L, "First");
        ProjectRequirement second =
                createExistingRequirement(document, 2L, "Second");

        ProjectRequirementsResponse response = requirementService.saveFinal(
                document.getProject().getId(),
                new SaveFinalRequirementsRequest(List.of(
                        finalRequirementItem(first, document, 2L),
                        finalRequirementItem(second, document, 1L)
                ))
        );

        assertThat(response.finalRequirements())
                .extracting(
                        detail -> detail.requirementId() + ":" + detail.externalReferenceId()
                )
                .containsExactly(
                        first.getId() + ":2",
                        second.getId() + ":1"
                );
        assertThat(requirementRepository.findByProjectIdOrderByIdAsc(
                document.getProject().getId()
        ))
                .extracting(ProjectRequirement::getActiveExternalReferenceId)
                .containsExactly(2L, 1L);
        verify(projectRepository)
                .findForUpdate(document.getProject().getId());
    }

    @Test
    void saveFinalAllowsReusingIdFromPreservedExcludedRequirement() {
        ProjectDocument document = createProjectDocument();
        ProjectRequirement previous =
                createExistingRequirement(document, 1L, "Previous");
        previous.setAiSuggestionJson("{}");
        requirementRepository.saveAndFlush(previous);
        SaveFinalRequirementsRequest.RequirementItem replacement =
                new SaveFinalRequirementsRequest.RequirementItem(
                        null,
                        null,
                        document.getId(),
                        1L,
                        RequirementType.FUNCTIONAL,
                        "Replacement",
                        "Replacement requirement",
                        null,
                        null,
                        null,
                        null,
                        document.getOriginalFileName(),
                        null,
                        RequirementPriority.HIGH
                );

        ProjectRequirementsResponse response = requirementService.saveFinal(
                document.getProject().getId(),
                new SaveFinalRequirementsRequest(List.of(replacement))
        );

        assertThat(response.finalRequirements())
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.requirementId()).isNotEqualTo(previous.getId());
                    assertThat(detail.externalReferenceId()).isEqualTo(1L);
                });
        List<ProjectRequirement> persisted =
                requirementRepository.findByProjectIdOrderByIdAsc(
                        document.getProject().getId()
                );
        assertThat(persisted).hasSize(2);
        ProjectRequirement excluded = persisted.stream()
                .filter(requirement -> requirement.getId().equals(previous.getId()))
                .findFirst()
                .orElseThrow();
        ProjectRequirement active = persisted.stream()
                .filter(ProjectRequirement::isIncludedInFinal)
                .findFirst()
                .orElseThrow();
        assertThat(excluded.getActiveExternalReferenceId()).isNull();
        assertThat(active.getActiveExternalReferenceId()).isEqualTo(1L);
    }

    @Test
    void concurrentAddedCandidatesReceiveDistinctExternalReferenceIds() throws Exception {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        ProjectRequirementChangeCandidate first =
                createApprovedAddedCandidate(document, "Concurrent A");
        ProjectRequirementChangeCandidate second =
                createApprovedAddedCandidate(document, "Concurrent B");

        runConcurrentApply(
                document.getProject().getId(),
                List.of(first.getId()),
                List.of(second.getId())
        );

        assertThat(requirementRepository.findByProjectIdOrderByIdAsc(
                document.getProject().getId()
        ))
                .extracting(ProjectRequirement::getExternalReferenceId)
                .containsExactly(1L, 2L, 3L)
                .doesNotHaveDuplicates();
        verify(requirementRepository, times(2))
                .findAllForUpdate(document.getProject().getId());
    }

    @Test
    void concurrentDuplicateCandidateApplyCreatesRequirementOnce() throws Exception {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);
        ProjectRequirementChangeCandidate candidate =
                createApprovedAddedCandidate(document, "Apply once");

        runConcurrentApply(
                document.getProject().getId(),
                List.of(candidate.getId()),
                List.of(candidate.getId())
        );

        assertThat(requirementRepository.findByProjectIdOrderByIdAsc(
                document.getProject().getId()
        ))
                .extracting(ProjectRequirement::getExternalReferenceId)
                .containsExactly(1L, 2L)
                .doesNotHaveDuplicates();
        assertThat(candidateRepository.findById(candidate.getId()).orElseThrow().getAppliedAt())
                .isNotNull();
    }

    @Test
    @WithMockUser(username = "STAFF-EVIDENCE", roles = "STAFF")
    void staffCannotCreateReadjustmentCandidates() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        )).isInstanceOf(AccessDeniedException.class);
    }

    private void prepareStoredContent() {
        byte[] content = "readjustment source".getBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder()
                                .contentLength((long) content.length)
                                .build(),
                        content
                )
        );
    }

    private PlanningRequirementReadjustResponse emptyReadjustmentResponse() {
        return new PlanningRequirementReadjustResponse(
                List.of(),
                List.of(),
                "SUCCEEDED"
        );
    }

    private ProjectRequirementChangeCandidate createApprovedAddedCandidate(
            ProjectDocument document,
            String title
    ) throws Exception {
        RequirementChangeProposal proposal = new RequirementChangeProposal(
                null,
                document.getId(),
                title,
                title + " requirement",
                RequirementType.FUNCTIONAL,
                RequirementPriority.MEDIUM,
                null,
                null,
                null,
                null,
                document.getOriginalFileName(),
                null,
                List.of()
        );
        ProjectRequirementChangeCandidate candidate =
                new ProjectRequirementChangeCandidate();
        candidate.setProject(projectRepository
                .findById(document.getProject().getId())
                .orElseThrow());
        candidate.setChangeType(RequirementChangeType.ADDED);
        candidate.setReviewStatus(RequirementChangeReviewStatus.APPROVED);
        candidate.setChangeReason("Concurrent apply test");
        candidate.setProposedRequirementJson(
                objectMapper.writeValueAsString(proposal)
        );
        candidate.setEvidencesJson("[]");
        candidate.setReviewedAt(LocalDateTime.now());
        return candidateRepository.saveAndFlush(candidate);
    }

    private void runConcurrentApply(
            Long projectId,
            List<Long> firstCandidateIds,
            List<Long> secondCandidateIds
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ProjectRequirementsResponse> first =
                    submitApply(executor, ready, start, projectId, firstCandidateIds);
            Future<ProjectRequirementsResponse> second =
                    submitApply(executor, ready, start, projectId, secondCandidateIds);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<ProjectRequirementsResponse> submitApply(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Long projectId,
            List<Long> candidateIds
    ) {
        return executor.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "PM-EVIDENCE",
                            "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_PM"))
                    )
            );
            try {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent apply did not start.");
                }
                return service.apply(projectId, candidateIds);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private SaveFinalRequirementsRequest.RequirementItem finalRequirementItem(
            ProjectRequirement requirement,
            ProjectDocument document,
            Long externalReferenceId
    ) {
        return new SaveFinalRequirementsRequest.RequirementItem(
                requirement.getId(),
                null,
                document.getId(),
                externalReferenceId,
                requirement.getType(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getAcceptanceCriteria(),
                requirement.getDueDate(),
                requirement.getDeliverableName(),
                requirement.getSecurityCondition(),
                document.getOriginalFileName(),
                requirement.getSourceExcerpt(),
                requirement.getPriority()
        );
    }

    private ProjectDocument createProjectDocument() {
        User pm = new User();
        pm.setEmployeeNumber("PM-EVIDENCE");
        pm.setName("Evidence PM");
        pm.setEmail("pm-evidence@example.com");
        pm.setPassword("encoded");
        pm.setRole("PM");
        pm.setStatus(UserStatus.ACTIVE);
        pm.setEmailVerified(true);
        userRepository.save(pm);

        Project project = new Project();
        project.setName("Evidence Project");
        project.setDescription("Evidence test");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        projectRepository.save(project);

        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.ANALYZED);
        document.setOriginalFileName("change.txt");
        document.setStoredFileName("stored.txt");
        document.setStoragePath("projects/evidence/change.txt");
        document.setExtension("txt");
        document.setContentType("text/plain");
        document.setFileSize(21);
        return documentRepository.save(document);
    }

    private ProjectRequirement createExistingRequirement(ProjectDocument document) {
        return createExistingRequirement(document, 1L, "Login");
    }

    private ProjectRequirement createExistingRequirement(
            ProjectDocument document,
            Long externalReferenceId,
            String title
    ) {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(document.getProject());
        requirement.setSourceDocument(document);
        requirement.setExternalReferenceId(externalReferenceId);
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setTitle(title);
        requirement.setDescription(title + " requirement");
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.UNCONFIRMED);
        requirement.setConfirmed(false);
        requirement.setIncludedInFinal(true);
        return requirementRepository.saveAndFlush(requirement);
    }
}
