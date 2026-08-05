package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.storage.DocumentObjectStorage;
import com.aivle26.aipm.Dto.project.OrganizationChartArtifactResponse;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateRequest;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Service.ArtifactVersionComparator;
import com.aivle26.aipm.client.ai.GeneratedOrganizationChart;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationChartArtifactService {

    private static final String CONTENT_TYPE = "image/jpeg";
    private static final String EXTENSION = "jpg";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final ProjectAuthorizationService projectAuthorizationService;
    private final PlanningResourceContextAssembler contextAssembler;
    private final PlanningResourceClient planningResourceClient;
    private final OrganizationChartArtifactPolicy artifactPolicy;
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository documentRepository;
    private final ProjectArtifactRepository artifactRepository;
    private final DocumentObjectStorage documentObjectStorage;
    private final ArtifactVersionComparator versionComparator;
    private final TransactionTemplate transactionTemplate;

    public OrganizationChartArtifactResponse generate(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        artifactPolicy.ensureForProject(projectId);

        PlanningResourceContextAssembler.PlanningResourceContext context =
                contextAssembler.assembleForOrganizationChart(projectId);
        OrganizationChartGenerateRequest aiRequest = new OrganizationChartGenerateRequest(
                context.aiRequest(),
                new OrganizationChartGenerateRequest.OrganizationMetadata(
                        context.projectManagerAiId(),
                        List.of()
                )
        );
        GeneratedOrganizationChart generated =
                planningResourceClient.generateOrganizationChart(aiRequest);
        byte[] image = generated.image();
        validateImage(image);

        String objectKey = "projects/%d/artifacts/organization-chart/%s.jpg".formatted(
                projectId,
                UUID.randomUUID()
        );
        storeImage(objectKey, image);

        try {
            OrganizationChartArtifactResponse saved = transactionTemplate.execute(status ->
                    persistGeneratedArtifact(projectId, objectKey, image.length)
            );
            if (saved == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ORGANIZATION_CHART_SAVE_FAILED",
                        "The organization chart could not be saved."
                );
            }
            return saved;
        } catch (RuntimeException exception) {
            cleanupObject(objectKey);
            throw exception;
        }
    }

    public OrganizationChartArtifactResponse getLatest(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        artifactPolicy.ensureForProject(projectId);
        return toResponse(requireLatestArtifact(projectId));
    }

    public OrganizationChartContent downloadLatest(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        ProjectArtifact artifact = requireLatestArtifact(projectId);
        ProjectDocument document = requireOwnedDocument(projectId, artifact);
        byte[] content;
        try {
            content = documentObjectStorage.get(document.getStoragePath());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_STORAGE_ERROR",
                    "The organization chart could not be downloaded.",
                    exception
            );
        }
        validateImage(content);
        return new OrganizationChartContent(
                "조직도-v" + artifact.getVersion() + ".jpg",
                CONTENT_TYPE,
                content
        );
    }

    private OrganizationChartArtifactResponse persistGeneratedArtifact(
            Long projectId,
            String objectKey,
            long fileSize
    ) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        artifactPolicy.ensure(project);
        String version = nextVersion(
                artifactRepository.findByProjectIdAndArtifactType(
                        projectId,
                        OrganizationChartArtifactPolicy.TYPE
                )
        );

        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.UPLOADED);
        document.setOriginalFileName("조직도.jpg");
        document.setStoredFileName(UUID.randomUUID() + ".jpg");
        document.setStoragePath(objectKey);
        document.setExtension(EXTENSION);
        document.setContentType(CONTENT_TYPE);
        document.setFileSize(fileSize);
        document.setFileType("ORGANIZATION_CHART");
        document.setProcessingMode("AI_GENERATED");
        ProjectDocument savedDocument = documentRepository.saveAndFlush(document);

        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setProject(project);
        artifact.setDocument(savedDocument);
        artifact.setArtifactType(OrganizationChartArtifactPolicy.TYPE);
        artifact.setArtifactName(OrganizationChartArtifactPolicy.NAME);
        artifact.setVersion(version);
        artifact.setApprovalStatus(ArtifactApprovalStatus.PENDING);
        ProjectArtifact savedArtifact = artifactRepository.saveAndFlush(artifact);
        return toResponse(savedArtifact);
    }

    private ProjectArtifact requireLatestArtifact(Long projectId) {
        return artifactRepository.findByProjectIdAndArtifactType(
                        projectId,
                        OrganizationChartArtifactPolicy.TYPE
                ).stream()
                .max(this::compareArtifacts)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ORGANIZATION_CHART_NOT_GENERATED",
                        "The organization chart has not been generated."
                ));
    }

    private int compareArtifacts(ProjectArtifact left, ProjectArtifact right) {
        int version = versionComparator.compare(left.getVersion(), right.getVersion());
        if (version != 0) {
            return version;
        }
        LocalDateTime leftTime = left.getUpdatedAt() == null
                ? LocalDateTime.MIN
                : left.getUpdatedAt();
        LocalDateTime rightTime = right.getUpdatedAt() == null
                ? LocalDateTime.MIN
                : right.getUpdatedAt();
        int updated = leftTime.compareTo(rightTime);
        if (updated != 0) {
            return updated;
        }
        return Comparator.nullsFirst(Long::compareTo).compare(left.getId(), right.getId());
    }

    private ProjectDocument requireOwnedDocument(Long projectId, ProjectArtifact artifact) {
        ProjectDocument document = artifact.getDocument();
        if (document == null
                || document.getProject() == null
                || !projectId.equals(document.getProject().getId())
                || document.getStoragePath() == null
                || document.getStoragePath().isBlank()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "ORGANIZATION_CHART_DOCUMENT_NOT_FOUND",
                    "The organization chart document was not found."
            );
        }
        return document;
    }

    private String nextVersion(List<ProjectArtifact> existing) {
        if (existing.isEmpty()) {
            return OrganizationChartArtifactPolicy.INITIAL_VERSION;
        }
        String latest = existing.stream()
                .map(ProjectArtifact::getVersion)
                .max(versionComparator)
                .orElse(OrganizationChartArtifactPolicy.INITIAL_VERSION);
        String[] segments = latest.split("\\.");
        segments[segments.length - 1] = new BigInteger(
                segments[segments.length - 1]
        ).add(BigInteger.ONE).toString();
        return String.join(".", segments);
    }

    private void storeImage(String objectKey, byte[] image) {
        try {
            documentObjectStorage.put(
                    objectKey,
                    CONTENT_TYPE,
                    image.length,
                    new ByteArrayInputStream(image)
            );
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_STORAGE_ERROR",
                    "The organization chart could not be uploaded.",
                    exception
            );
        }
    }

    private void validateImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "EMPTY_ORGANIZATION_CHART_IMAGE",
                    "The generated organization chart image is empty."
            );
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_IMAGE_TOO_LARGE",
                    "The generated organization chart exceeds the size limit."
            );
        }
        if (image.length < 3
                || (image[0] & 0xff) != 0xff
                || (image[1] & 0xff) != 0xd8
                || (image[2] & 0xff) != 0xff) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_ORGANIZATION_CHART_IMAGE",
                    "The generated organization chart is not a JPEG image."
            );
        }
    }

    private void cleanupObject(String objectKey) {
        try {
            documentObjectStorage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Failed to remove an organization chart object after persistence failure",
                    cleanupFailure
            );
        }
    }

    private OrganizationChartArtifactResponse toResponse(ProjectArtifact artifact) {
        ProjectDocument document = requireOwnedDocument(
                artifact.getProject().getId(),
                artifact
        );
        Long projectId = artifact.getProject().getId();
        String downloadPath = "/api/projects/%d/artifacts/organization-chart/latest/download"
                .formatted(projectId);
        return new OrganizationChartArtifactResponse(
                artifact.getId(),
                projectId,
                artifact.getArtifactType(),
                artifact.getArtifactName(),
                artifact.getVersion(),
                artifact.getApprovalStatus(),
                document.getContentType(),
                document.getFileSize(),
                artifact.getCreatedAt(),
                downloadPath,
                downloadPath
        );
    }

    public record OrganizationChartContent(
            String fileName,
            String contentType,
            byte[] content
    ) {
        public OrganizationChartContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
