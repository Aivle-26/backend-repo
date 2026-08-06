package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.storage.DocumentObjectStorage;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Service.ArtifactVersionComparator;
import com.aivle26.aipm.client.ai.GeneratedOrganizationChart;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationChartArtifactServiceTest {

    private static final byte[] JPEG = {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, 0x02
    };

    @Mock private ProjectAuthorizationService projectAuthorizationService;
    @Mock private PlanningResourceContextAssembler contextAssembler;
    @Mock private PlanningResourceClient planningResourceClient;
    @Mock private OrganizationChartArtifactPolicy artifactPolicy;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectDocumentRepository documentRepository;
    @Mock private ProjectArtifactRepository artifactRepository;
    @Mock private DocumentObjectStorage documentObjectStorage;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;

    private OrganizationChartArtifactService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new OrganizationChartArtifactService(
                projectAuthorizationService,
                contextAssembler,
                planningResourceClient,
                artifactPolicy,
                projectRepository,
                documentRepository,
                artifactRepository,
                documentObjectStorage,
                new ArtifactVersionComparator(),
                transactionTemplate
        );
        project = new Project();
        project.setId(1L);
        project.setName("Organization Project");

    }

    @Test
    void generatesStoresAndPersistsOrganizationChart() {
        arrangeGeneration();
        AtomicReference<byte[]> storedBytes = new AtomicReference<>();
        doAnswer(invocation -> {
            storedBytes.set(invocation.<java.io.InputStream>getArgument(3).readAllBytes());
            return null;
        }).when(documentObjectStorage).put(any(), eq("image/jpeg"), eq(5L), any());

        var response = service.generate(1L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentObjectStorage).put(
                keyCaptor.capture(),
                eq("image/jpeg"),
                eq(5L),
                any()
        );
        assertThat(keyCaptor.getValue()).matches(
                "projects/1/artifacts/organization-chart/[0-9a-f-]{36}\\.jpg"
        );
        assertThat(storedBytes.get()).containsExactly(JPEG);
        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.version()).isEqualTo("1.0");
        assertThat(response.approvalStatus()).isEqualTo(ArtifactApprovalStatus.PENDING);
        assertThat(response.downloadUrl()).isEqualTo(
                "/api/projects/1/artifacts/organization-chart/latest/download"
        );
    }

    @Test
    void regenerationCreatesNextVersionWithoutDeletingHistory() {
        arrangeGeneration();
        ProjectArtifact old = artifact("1.10", 19L);
        when(artifactRepository.findByProjectIdAndArtifactType(
                1L,
                OrganizationChartArtifactPolicy.TYPE
        )).thenReturn(List.of(old));

        var response = service.generate(1L);

        assertThat(response.version()).isEqualTo("1.11");
        verify(artifactRepository, never()).delete(any());
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void deletesUploadedObjectWhenDatabasePersistenceFails() {
        arrangeGeneration();
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(documentRepository)
                .saveAndFlush(any(ProjectDocument.class));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        assertThatThrownBy(() -> service.generate(1L))
                .isInstanceOf(IllegalStateException.class);

        verify(documentObjectStorage).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith(
                "projects/1/artifacts/organization-chart/"
        );
    }

    @Test
    void returnsLatestVersionAndDownloadsStoredBytes() {
        ProjectArtifact old = artifact("1.2", 20L);
        ProjectArtifact latest = artifact("1.10", 21L);
        when(artifactRepository.findByProjectIdAndArtifactType(
                1L,
                OrganizationChartArtifactPolicy.TYPE
        )).thenReturn(List.of(old, latest));
        when(documentObjectStorage.get(latest.getDocument().getStoragePath()))
                .thenReturn(JPEG);

        var metadata = service.getLatest(1L);
        var download = service.downloadLatest(1L);

        assertThat(metadata.version()).isEqualTo("1.10");
        assertThat(download.contentType()).isEqualTo("image/jpeg");
        assertThat(download.content()).containsExactly(JPEG);
        verify(documentObjectStorage).get(
                "projects/1/artifacts/organization-chart/1.10.jpg"
        );
    }

    @Test
    void blocksDownloadBeforeReadingStorageWhenProjectAccessFails() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access is denied"))
                .when(projectAuthorizationService)
                .requireProjectAccess(1L);

        assertThatThrownBy(() -> service.downloadLatest(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(documentObjectStorage, never()).get(any());
    }

    private ProjectArtifact artifact(String version, Long id) {
        ProjectDocument document = new ProjectDocument();
        document.setId(id + 100L);
        document.setProject(project);
        document.setContentType("image/jpeg");
        document.setFileSize(JPEG.length);
        document.setStoragePath(
                "projects/1/artifacts/organization-chart/" + version + ".jpg"
        );

        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setId(id);
        artifact.setProject(project);
        artifact.setDocument(document);
        artifact.setArtifactType(OrganizationChartArtifactPolicy.TYPE);
        artifact.setArtifactName(OrganizationChartArtifactPolicy.NAME);
        artifact.setVersion(version);
        artifact.setApprovalStatus(ArtifactApprovalStatus.PENDING);
        artifact.onCreate();
        return artifact;
    }

    private void arrangeGeneration() {
        PlanningResourceRecommendRequest aiRequest = new PlanningResourceRecommendRequest(
                1L,
                "Organization Project",
                List.of(),
                List.of()
        );
        var context = new PlanningResourceContextAssembler.PlanningResourceContext(
                project,
                null,
                List.of(),
                java.util.Map.of(),
                aiRequest,
                7L,
                List.of()
        );
        when(contextAssembler.assembleForOrganizationChart(1L)).thenReturn(context);
        when(planningResourceClient.generateOrganizationChart(any()))
                .thenReturn(generatedChart());
        when(projectRepository.findForUpdate(1L)).thenReturn(Optional.of(project));
        when(artifactRepository.findByProjectIdAndArtifactType(
                1L,
                OrganizationChartArtifactPolicy.TYPE
        )).thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(documentRepository.saveAndFlush(any(ProjectDocument.class)))
                .thenAnswer(invocation -> {
                    ProjectDocument document = invocation.getArgument(0);
                    document.setId(10L);
                    document.onCreate();
                    return document;
                });
        org.mockito.Mockito.lenient()
                .when(artifactRepository.saveAndFlush(any(ProjectArtifact.class)))
                .thenAnswer(invocation -> {
                    ProjectArtifact artifact = invocation.getArgument(0);
                    artifact.setId(20L);
                    artifact.onCreate();
                    return artifact;
                });
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    private GeneratedOrganizationChart generatedChart() {
        var organization = new OrganizationChartGenerateResponse.OrganizationView(
                1L,
                7L,
                List.of(),
                List.of(),
                List.of(),
                OffsetDateTime.parse("2026-08-05T10:00:00Z")
        );
        var response = new OrganizationChartGenerateResponse(
                organization,
                "project-1-organization-chart.jpg",
                "image/jpeg",
                "ignored-after-http-client-validation",
                1200,
                900
        );
        return new GeneratedOrganizationChart(response, JPEG);
    }
}
