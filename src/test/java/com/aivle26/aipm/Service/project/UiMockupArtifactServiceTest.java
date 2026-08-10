package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.storage.DocumentObjectStorage;
import com.aivle26.aipm.Dto.project.UiMockupGenerateResponse;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Service.ArtifactVersionComparator;
import com.aivle26.aipm.client.ai.GeneratedUiMockup;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiMockupArtifactServiceTest {

    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2};

    @Mock private ProjectAuthorizationService authorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectRequirementRepository requirementRepository;
    @Mock private PlanningResourceClient planningResourceClient;
    @Mock private UiMockupArtifactPolicy artifactPolicy;
    @Mock private ProjectDocumentRepository documentRepository;
    @Mock private ProjectArtifactRepository artifactRepository;
    @Mock private DocumentObjectStorage objectStorage;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;

    private UiMockupArtifactService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new UiMockupArtifactService(
                authorizationService,
                projectRepository,
                requirementRepository,
                planningResourceClient,
                artifactPolicy,
                documentRepository,
                artifactRepository,
                objectStorage,
                new ArtifactVersionComparator(),
                transactionTemplate
        );
        project = new Project();
        project.setId(1L);
        project.setName("AIPM");
        project.setDescription("Project management platform");
    }

    @Test
    void pmGeneratesStoresAndPersistsVersionOne() {
        arrangeGeneration(List.of(requirement()), List.of());

        var response = service.generate(1L);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).put(key.capture(), eq("image/jpeg"), eq(5L), any());
        assertThat(key.getValue()).matches("projects/1/artifacts/ui-mockup/[0-9a-f-]{36}\\.jpg");
        assertThat(response.artifactType()).isEqualTo(UiMockupArtifactPolicy.TYPE);
        assertThat(response.version()).isEqualTo("1.0");
        assertThat(response.downloadUrl()).isEqualTo(
                "/api/projects/1/artifacts/ui-mockup/latest/download"
        );
    }

    @Test
    void regenerationIncrementsMinorVersionWithoutDeletingHistory() {
        arrangeGeneration(List.of(requirement()), List.of(artifact("1.0", 10L)));

        var response = service.generate(1L);

        assertThat(response.version()).isEqualTo("1.1");
        verify(artifactRepository, never()).delete(any());
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void rejectsGenerationWhenConfirmedRequirementsAreMissing() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(requirementRepository.findByProjectIdAndStatus(1L, RequirementStatus.CONFIRMED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(1L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("CONFIRMED_REQUIREMENT_NOT_FOUND");
                });
        verify(planningResourceClient, never()).generateUiMockup(any());
        verify(objectStorage, never()).put(any(), any(), any(Long.class), any());
    }

    @Test
    void rejectsNonPmBeforeReadingProjectData() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requireProjectPm(1L);

        assertThatThrownBy(() -> service.generate(1L))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).findById(any());
        verify(planningResourceClient, never()).generateUiMockup(any());
    }

    @Test
    void rejectsInvalidJpegBeforeS3Upload() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(requirementRepository.findByProjectIdAndStatus(1L, RequirementStatus.CONFIRMED))
                .thenReturn(List.of(requirement()));
        when(planningResourceClient.generateUiMockup(any())).thenReturn(generated(new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> service.generate(1L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_UI_MOCKUP_IMAGE")
                );
        verify(objectStorage, never()).put(any(), any(), any(Long.class), any());
    }

    @Test
    void latestAndDownloadUseNewestStoredArtifact() {
        ProjectArtifact old = artifact("1.0", 10L);
        ProjectArtifact latest = artifact("1.1", 11L);
        when(artifactRepository.findByProjectIdAndArtifactType(1L, UiMockupArtifactPolicy.TYPE))
                .thenReturn(List.of(old, latest));
        when(objectStorage.get(latest.getDocument().getStoragePath())).thenReturn(JPEG);

        assertThat(service.getLatest(1L).version()).isEqualTo("1.1");
        assertThat(service.downloadLatest(1L).content()).containsExactly(JPEG);
    }

    private void arrangeGeneration(
            List<ProjectRequirement> requirements,
            List<ProjectArtifact> existing
    ) {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(requirementRepository.findByProjectIdAndStatus(1L, RequirementStatus.CONFIRMED))
                .thenReturn(requirements);
        when(planningResourceClient.generateUiMockup(any())).thenReturn(generated(JPEG));
        when(projectRepository.findForUpdate(1L)).thenReturn(Optional.of(project));
        when(artifactRepository.findByProjectIdAndArtifactType(1L, UiMockupArtifactPolicy.TYPE))
                .thenReturn(existing);
        when(documentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ProjectDocument document = invocation.getArgument(0);
            document.setId(100L);
            document.onCreate();
            return document;
        });
        when(artifactRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ProjectArtifact artifact = invocation.getArgument(0);
            artifact.setId(200L);
            artifact.onCreate();
            return artifact;
        });
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    private ProjectRequirement requirement() {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setId(7L);
        requirement.setProject(project);
        requirement.setTitle("Dashboard");
        requirement.setDescription("Show project status");
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.CONFIRMED);
        requirement.setConfirmed(true);
        return requirement;
    }

    private ProjectArtifact artifact(String version, Long id) {
        ProjectDocument document = new ProjectDocument();
        document.setId(id + 100L);
        document.setProject(project);
        document.setContentType("image/jpeg");
        document.setFileSize(JPEG.length);
        document.setStoragePath("projects/1/artifacts/ui-mockup/" + version + ".jpg");

        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setId(id);
        artifact.setProject(project);
        artifact.setDocument(document);
        artifact.setArtifactType(UiMockupArtifactPolicy.TYPE);
        artifact.setArtifactName(UiMockupArtifactPolicy.NAME);
        artifact.setVersion(version);
        artifact.setApprovalStatus(ArtifactApprovalStatus.PENDING);
        artifact.onCreate();
        return artifact;
    }

    private GeneratedUiMockup generated(byte[] image) {
        UiMockupGenerateResponse response = new UiMockupGenerateResponse(
                1L,
                new ObjectMapper().createObjectNode().put("project_title", "AIPM"),
                "project-1-ui-mockup.jpg",
                "image/jpeg",
                "ignored",
                1920,
                1080
        );
        return new GeneratedUiMockup(response, image);
    }
}
