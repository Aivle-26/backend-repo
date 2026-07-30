package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Dto.project.PlanningRequirementReadjustResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.RequirementReadjustmentResponse;
import com.aivle26.aipm.Dto.project.ReviewRequirementChangeRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.RequirementChangeReviewStatus;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.client.ai.PlanningAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@WithMockUser(username = "PM-EVIDENCE", roles = "PM")
class ProjectRequirementReadjustmentServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private PlanningAgentClient planningAgentClient;

    @Autowired
    private ProjectRequirementReadjustmentService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectDocumentRepository documentRepository;

    @Autowired
    private ProjectRequirementRepository requirementRepository;

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
    @WithMockUser(username = "STAFF-EVIDENCE", roles = "STAFF")
    void staffCannotCreateReadjustmentCandidates() {
        ProjectDocument document = createProjectDocument();
        createExistingRequirement(document);

        assertThatThrownBy(() -> service.createCandidates(
                document.getProject().getId(),
                List.of(document.getId())
        )).isInstanceOf(AccessDeniedException.class);
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

    private void createExistingRequirement(ProjectDocument document) {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(document.getProject());
        requirement.setSourceDocument(document);
        requirement.setExternalReferenceId(1L);
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setTitle("Login");
        requirement.setDescription("Users must log in.");
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.UNCONFIRMED);
        requirement.setConfirmed(false);
        requirement.setIncludedInFinal(true);
        requirementRepository.save(requirement);
    }
}
