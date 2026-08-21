package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.storage.DocumentObjectStorage;
import com.aivle26.aipm.Dto.project.UiMockupArtifactResponse;
import com.aivle26.aipm.Dto.project.UiMockupGenerateRequest;
import com.aivle26.aipm.Dto.project.UiMockupAssessmentResponse;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Service.ArtifactVersionComparator;
import com.aivle26.aipm.client.ai.GeneratedUiMockup;
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
public class UiMockupArtifactService {

    private static final String CONTENT_TYPE = "image/jpeg";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_REQUIREMENTS = 60;

    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final PlanningResourceClient planningResourceClient;
    private final UiMockupArtifactPolicy artifactPolicy;
    private final ProjectDocumentRepository documentRepository;
    private final ProjectArtifactRepository artifactRepository;
    private final DocumentObjectStorage documentObjectStorage;
    private final ArtifactVersionComparator versionComparator;
    private final TransactionTemplate transactionTemplate;

    public UiMockupArtifactResponse generate(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        List<ProjectRequirement> requirements = requirementRepository.findByProjectIdAndStatus(
                projectId,
                RequirementStatus.CONFIRMED
        );
        if (requirements.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONFIRMED_REQUIREMENT_NOT_FOUND",
                    "UI mockup generation requires at least one confirmed requirement."
            );
        }
        artifactPolicy.ensureForProject(projectId);
        GeneratedUiMockup generated = planningResourceClient.generateUiMockup(
                toAiRequest(project, requirements)
        );
        byte[] image = generated.image();
        validateImage(image);

        String objectKey = "projects/%d/artifacts/ui-mockup/%s.jpg".formatted(
                projectId,
                UUID.randomUUID()
        );
        storeImage(objectKey, image);
        try {
            UiMockupArtifactResponse saved = transactionTemplate.execute(status ->
                    persist(projectId, objectKey, image.length)
            );
            if (saved == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "UI_MOCKUP_SAVE_FAILED",
                        "The UI mockup could not be saved."
                );
            }
            return saved;
        } catch (RuntimeException exception) {
            cleanupObject(objectKey);
            throw exception;
        }
    }

    public UiMockupAssessmentResponse assess(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        List<ProjectRequirement> requirements = requirementRepository.findByProjectIdAndStatus(
                projectId,
                RequirementStatus.CONFIRMED
        );
        if (requirements.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONFIRMED_REQUIREMENT_NOT_FOUND",
                    "UI mockup assessment requires at least one confirmed requirement."
            );
        }
        return planningResourceClient.assessUiMockup(toAiRequest(project, requirements));
    }

    public UiMockupArtifactResponse getLatest(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        return toResponse(requireLatest(projectId));
    }

    public UiMockupContent downloadLatest(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        ProjectArtifact artifact = requireLatest(projectId);
        ProjectDocument document = requireOwnedDocument(projectId, artifact);
        byte[] content;
        try {
            content = documentObjectStorage.get(document.getStoragePath());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "UI_MOCKUP_STORAGE_ERROR",
                    "The UI mockup could not be downloaded.",
                    exception
            );
        }
        validateImage(content);
        return new UiMockupContent("UI-목업-v" + artifact.getVersion() + ".jpg", content);
    }

    private UiMockupGenerateRequest toAiRequest(
            Project project,
            List<ProjectRequirement> requirements
    ) {
        List<UiMockupGenerateRequest.ConfirmedRequirement> summarized = requirements.stream()
                .limit(MAX_REQUIREMENTS)
                .map(requirement -> new UiMockupGenerateRequest.ConfirmedRequirement(
                        requirement.getId(),
                        requirement.getTitle(),
                        limit(requirement.getDescription(), 1000),
                        requirement.getType().name(),
                        requirement.getPriority().name()
                ))
                .toList();
        return new UiMockupGenerateRequest(
                project.getId(),
                project.getName(),
                limit(project.getDescription(), 2000),
                summarized
        );
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private UiMockupArtifactResponse persist(Long projectId, String objectKey, long fileSize) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        artifactPolicy.ensure(project);
        String version = nextVersion(artifactRepository.findByProjectIdAndArtifactType(
                projectId,
                UiMockupArtifactPolicy.TYPE
        ));

        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.UPLOADED);
        document.setOriginalFileName("UI-목업.jpg");
        document.setStoredFileName(UUID.randomUUID() + ".jpg");
        document.setStoragePath(objectKey);
        document.setExtension("jpg");
        document.setContentType(CONTENT_TYPE);
        document.setFileSize(fileSize);
        document.setFileType("UI_MOCKUP");
        document.setProcessingMode("AI_GENERATED");
        ProjectDocument savedDocument = documentRepository.saveAndFlush(document);

        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setProject(project);
        artifact.setDocument(savedDocument);
        artifact.setArtifactType(UiMockupArtifactPolicy.TYPE);
        artifact.setArtifactName(UiMockupArtifactPolicy.NAME);
        artifact.setVersion(version);
        artifact.setApprovalStatus(ArtifactApprovalStatus.PENDING);
        return toResponse(artifactRepository.saveAndFlush(artifact));
    }

    private ProjectArtifact requireLatest(Long projectId) {
        return artifactRepository.findByProjectIdAndArtifactType(
                        projectId,
                        UiMockupArtifactPolicy.TYPE
                ).stream()
                .max(this::compareArtifacts)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "UI_MOCKUP_NOT_GENERATED",
                        "The UI mockup has not been generated."
                ));
    }

    private int compareArtifacts(ProjectArtifact left, ProjectArtifact right) {
        int version = versionComparator.compare(left.getVersion(), right.getVersion());
        if (version != 0) {
            return version;
        }
        LocalDateTime leftTime = left.getUpdatedAt() == null ? LocalDateTime.MIN : left.getUpdatedAt();
        LocalDateTime rightTime = right.getUpdatedAt() == null ? LocalDateTime.MIN : right.getUpdatedAt();
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
                    "UI_MOCKUP_DOCUMENT_NOT_FOUND",
                    "The UI mockup document was not found."
            );
        }
        return document;
    }

    private String nextVersion(List<ProjectArtifact> existing) {
        if (existing.isEmpty()) {
            return UiMockupArtifactPolicy.INITIAL_VERSION;
        }
        String latest = existing.stream()
                .map(ProjectArtifact::getVersion)
                .max(versionComparator)
                .orElse(UiMockupArtifactPolicy.INITIAL_VERSION);
        String[] segments = latest.split("\\.");
        segments[segments.length - 1] = new BigInteger(segments[segments.length - 1])
                .add(BigInteger.ONE)
                .toString();
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
                    "UI_MOCKUP_STORAGE_ERROR",
                    "The UI mockup could not be uploaded.",
                    exception
            );
        }
    }

    private void validateImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMPTY_UI_MOCKUP_IMAGE", "The UI mockup image is empty.");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "UI_MOCKUP_IMAGE_TOO_LARGE", "The UI mockup exceeds the size limit.");
        }
        if (image.length < 3
                || (image[0] & 0xff) != 0xff
                || (image[1] & 0xff) != 0xd8
                || (image[2] & 0xff) != 0xff) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_UI_MOCKUP_IMAGE", "The UI mockup is not a JPEG image.");
        }
    }

    private void cleanupObject(String objectKey) {
        try {
            documentObjectStorage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to remove a UI mockup object after persistence failure", cleanupFailure);
        }
    }

    private UiMockupArtifactResponse toResponse(ProjectArtifact artifact) {
        ProjectDocument document = requireOwnedDocument(artifact.getProject().getId(), artifact);
        Long projectId = artifact.getProject().getId();
        String path = "/api/projects/%d/artifacts/ui-mockup/latest/download".formatted(projectId);
        return new UiMockupArtifactResponse(
                artifact.getId(),
                projectId,
                artifact.getArtifactType(),
                artifact.getArtifactName(),
                artifact.getVersion(),
                artifact.getApprovalStatus(),
                document.getContentType(),
                document.getFileSize(),
                artifact.getCreatedAt(),
                path,
                path
        );
    }

    public record UiMockupContent(String fileName, byte[] content) {
        public UiMockupContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
