package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.DocumentStorageProperties;
import com.aivle26.aipm.Config.S3Properties;
import com.aivle26.aipm.Dto.ProjectDocumentUploadItemResponse;
import com.aivle26.aipm.Dto.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectDocumentService {
    private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.of(
            "pdf", Set.of("application/pdf"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            "txt", Set.of("text/plain")
    );
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;
    private static final int MAX_SANITIZED_FILE_NAME_LENGTH = 200;

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentStorageProperties documentStorageProperties;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Transactional
    public ProjectDocumentUploadResponse uploadInitialDocuments(Long projectId, List<MultipartFile> files) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
        projectAuthorizationService.requireProjectPm(projectId);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is required");
        }

        List<UploadCandidate> candidates = files.stream()
                .map(this::prepareFile)
                .toList();
        List<String> uploadedObjectKeys = new ArrayList<>();
        try {
            List<ProjectDocument> documents = new ArrayList<>();
            for (UploadCandidate candidate : candidates) {
                StoredFile storedFile = uploadToS3(projectId, candidate);
                uploadedObjectKeys.add(storedFile.objectKey());

                ProjectDocument document = new ProjectDocument();
                document.setProject(project);
                document.setStatus(ProjectDocumentStatus.UPLOADED);
                document.setOriginalFileName(storedFile.originalFileName());
                document.setStoredFileName(storedFile.storedFileName());
                document.setStoragePath(storedFile.objectKey());
                document.setExtension(storedFile.extension());
                document.setContentType(storedFile.contentType());
                document.setFileSize(storedFile.fileSize());
                documents.add(document);
            }

            List<ProjectDocument> savedDocuments = projectDocumentRepository.saveAllAndFlush(documents);
            return new ProjectDocumentUploadResponse(
                    project.getId(),
                    savedDocuments.stream()
                            .map(document -> new ProjectDocumentUploadItemResponse(
                                    document.getId(),
                                    document.getOriginalFileName(),
                                    document.getStatus(),
                                    document.getFileSize()
                            ))
                            .toList()
            );
        } catch (RuntimeException exception) {
            cleanupUploadedObjects(uploadedObjectKeys);
            throw exception;
        }
    }

    private UploadCandidate prepareFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }

        String normalizedFileName = normalizeFileName(file.getOriginalFilename());
        String extension = extractExtension(normalizedFileName);
        String contentType = normalizeContentType(file.getContentType());
        validateExtension(extension);
        validateMimeType(extension, contentType);
        validateFileSize(file.getSize());

        return new UploadCandidate(
                file,
                normalizedFileName,
                sanitizeFileName(normalizedFileName, extension),
                extension,
                contentType,
                file.getSize()
        );
    }

    private StoredFile uploadToS3(Long projectId, UploadCandidate candidate) {
        UUID uploadId = UUID.randomUUID();
        String storedFileName = uploadId + "." + candidate.extension();
        String objectKey = "projects/%d/documents/%s/%s".formatted(
                projectId,
                uploadId,
                candidate.sanitizedFileName()
        );
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(candidate.contentType())
                .contentLength(candidate.fileSize())
                .build();

        try (InputStream inputStream = candidate.file().getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, candidate.fileSize()));
            return new StoredFile(
                    candidate.originalFileName(),
                    storedFileName,
                    candidate.extension(),
                    candidate.contentType(),
                    candidate.fileSize(),
                    objectKey
            );
        } catch (IOException | SdkException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "DOCUMENT_STORAGE_ERROR",
                    "file upload failed",
                    exception
            );
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }

        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFC).replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank()
                || baseName.length() > MAX_ORIGINAL_FILE_NAME_LENGTH
                || ".".equals(baseName)
                || "..".equals(baseName)
                || baseName.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
        return baseName;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String originalFileName, String extension) {
        int dotIndex = originalFileName.lastIndexOf('.');
        String sanitizedBaseName = originalFileName.substring(0, dotIndex)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (sanitizedBaseName.isBlank()) {
            sanitizedBaseName = "document";
        }

        int maxBaseNameLength = MAX_SANITIZED_FILE_NAME_LENGTH - extension.length() - 1;
        if (sanitizedBaseName.length() > maxBaseNameLength) {
            sanitizedBaseName = sanitizedBaseName.substring(0, maxBaseNameLength);
        }
        return sanitizedBaseName + "." + extension;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        if (!documentStorageProperties.getAllowedExtensions().contains(extension)
                || !MIME_TYPES_BY_EXTENSION.containsKey(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
    }

    private void validateMimeType(String extension, String contentType) {
        if (!documentStorageProperties.getAllowedMimeTypes().contains(contentType)
                || !MIME_TYPES_BY_EXTENSION.get(extension).contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
    }

    private void validateFileSize(long fileSize) {
        if (fileSize > documentStorageProperties.getMaxFileSize()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file size exceeded");
        }
    }

    private void cleanupUploadedObjects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(objectKey)
                        .build());
            } catch (RuntimeException cleanupException) {
                log.warn("Failed to delete an S3 object after document upload rollback", cleanupException);
            }
        }
    }

    record UploadCandidate(
            MultipartFile file,
            String originalFileName,
            String sanitizedFileName,
            String extension,
            String contentType,
            long fileSize
    ) {
    }

    record StoredFile(
            String originalFileName,
            String storedFileName,
            String extension,
            String contentType,
            long fileSize,
            String objectKey
    ) {
    }
}
