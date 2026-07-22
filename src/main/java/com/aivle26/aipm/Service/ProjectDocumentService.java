package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.DocumentStorageProperties;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDocumentService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentStorageProperties documentStorageProperties;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    public ProjectDocumentUploadResponse uploadInitialDocuments(Long projectId, List<MultipartFile> files) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is required");
        }

        List<Path> savedPaths = new ArrayList<>();
        try {
            List<ProjectDocument> documents = new ArrayList<>();
            for (MultipartFile file : files) {
                StoredFile storedFile = storeFile(file);
                savedPaths.add(storedFile.path());

                ProjectDocument document = new ProjectDocument();
                document.setProject(project);
                document.setStatus(ProjectDocumentStatus.UPLOADED);
                document.setOriginalFileName(storedFile.originalFileName());
                document.setStoredFileName(storedFile.storedFileName());
                document.setStoragePath(storedFile.path().toString());
                document.setExtension(storedFile.extension());
                document.setContentType(storedFile.contentType());
                document.setFileSize(storedFile.fileSize());
                documents.add(document);
            }

            List<ProjectDocument> savedDocuments = projectDocumentRepository.saveAll(documents);
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
            cleanupFiles(savedPaths);
            throw exception;
        }
    }

    private StoredFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }

        String normalizedFileName = normalizeFileName(file.getOriginalFilename());
        String extension = extractExtension(normalizedFileName);
        validateExtension(extension);
        validateMimeType(file.getContentType());
        validateFileSize(file.getSize());

        Path rootDirectory = Path.of(documentStorageProperties.getStoragePath()).toAbsolutePath().normalize();
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = rootDirectory.resolve(storedFileName).normalize();
        if (!targetPath.startsWith(rootDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }

        try {
            Files.createDirectories(rootDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    normalizedFileName,
                    storedFileName,
                    extension,
                    file.getContentType(),
                    file.getSize(),
                    targetPath
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "file upload failed");
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }

        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFC).replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName) || baseName.contains("..")) {
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

    private void validateExtension(String extension) {
        if (!documentStorageProperties.getAllowedExtensions().contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
    }

    private void validateMimeType(String contentType) {
        if (contentType == null || !documentStorageProperties.getAllowedMimeTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid file");
        }
    }

    private void validateFileSize(long fileSize) {
        if (fileSize > documentStorageProperties.getMaxFileSize()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file size exceeded");
        }
    }

    private void cleanupFiles(List<Path> savedPaths) {
        for (Path path : savedPaths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    record StoredFile(
            String originalFileName,
            String storedFileName,
            String extension,
            String contentType,
            long fileSize,
            Path path
    ) {
    }
}
