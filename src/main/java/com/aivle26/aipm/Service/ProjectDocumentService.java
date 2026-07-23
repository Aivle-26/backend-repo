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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDocumentService {
    private static final int MAX_FILE_COUNT = 10;

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentStorageProperties documentStorageProperties;

    @Transactional
    public ProjectDocumentUploadResponse uploadInitialDocuments(Long projectId, List<MultipartFile> files) {
        Project project = loadDraftProject(projectId);
        List<ProjectDocument> savedDocuments = replaceProjectDocuments(project, validateUploadFiles(files), ProjectDocumentStatus.UPLOADED);
        return buildUploadResponse(project.getId(), savedDocuments);
    }

    @Transactional(readOnly = true)
    public List<StoredDocumentFile> getStoredDocumentFiles(Long projectId) {
        return loadStoredDocumentFiles(loadProjectDocuments(projectId, true));
    }

    @Transactional(readOnly = true)
    public List<ProjectDocument> getProjectDocuments(Long projectId) {
        return loadProjectDocuments(projectId, false);
    }

    @Transactional
    public void deleteProjectDocumentRecords(Long projectId) {
        projectDocumentRepository.deleteAllByProjectId(projectId);
    }

    @Transactional
    public void deleteProjectDocumentFiles(Long projectId) {
        List<ProjectDocument> documents = projectDocumentRepository.findByProjectId(projectId);
        if (!documents.isEmpty()) {
            deleteStoredFiles(documents);
        }
    }

    List<ValidatedUploadFile> validateUploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_DOCUMENT_REQUIRED", "At least one document must be uploaded.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_PROJECT_DOCUMENTS", "A maximum of 10 documents can be uploaded.");
        }

        Set<String> normalizedFileNames = new HashSet<>();
        List<ValidatedUploadFile> validatedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_DOCUMENT_EMPTY", "Empty files are not allowed.");
            }

            String originalFileName = normalizeFileName(file.getOriginalFilename());
            if (!normalizedFileNames.add(originalFileName)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_PROJECT_DOCUMENT_NAME", "Duplicate file name: " + originalFileName);
            }

            String extension = extractExtension(originalFileName);
            validateExtension(extension, originalFileName);
            validateMimeType(file.getContentType(), originalFileName);
            validateFileSize(file.getSize(), originalFileName);
            validatedFiles.add(new ValidatedUploadFile(file, originalFileName, extension, file.getContentType(), file.getSize()));
        }
        return validatedFiles;
    }

    List<ProjectDocument> replaceProjectDocuments(Project project, List<ValidatedUploadFile> validatedFiles, ProjectDocumentStatus status) {
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (validatedFiles == null || validatedFiles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_DOCUMENT_REQUIRED", "At least one document must be uploaded.");
        }

        List<ProjectDocument> replacedDocuments = new ArrayList<>();
        List<ProjectDocument> createdDocuments = new ArrayList<>();
        List<Path> createdPaths = new ArrayList<>();

        try {
            for (ValidatedUploadFile validatedFile : validatedFiles) {
                projectDocumentRepository.findByProjectIdAndOriginalFileName(project.getId(), validatedFile.originalFileName())
                        .ifPresent(replacedDocuments::add);
            }

            for (ValidatedUploadFile validatedFile : validatedFiles) {
                StoredFile storedFile = storeValidatedFile(validatedFile);
                createdPaths.add(storedFile.path());
                createdDocuments.add(toProjectDocument(project, status, storedFile));
            }

            deleteStoredFiles(replacedDocuments);
            if (!replacedDocuments.isEmpty()) {
                projectDocumentRepository.deleteAll(replacedDocuments);
                projectDocumentRepository.flush();
            }

            return projectDocumentRepository.saveAll(createdDocuments);
        } catch (RuntimeException exception) {
            cleanupFiles(createdPaths);
            throw exception;
        }
    }

    void cleanupStoredFiles(List<ProjectDocument> documents) {
        deleteStoredFiles(documents);
    }

    ProjectDocumentUploadResponse buildUploadResponse(Long projectId, List<ProjectDocument> savedDocuments) {
        return new ProjectDocumentUploadResponse(
                projectId,
                savedDocuments.stream()
                        .map(document -> new ProjectDocumentUploadItemResponse(
                                document.getId(),
                                document.getOriginalFileName(),
                                document.getStatus(),
                                document.getFileSize()
                        ))
                        .toList()
        );
    }

    private List<ProjectDocument> loadProjectDocuments(Long projectId, boolean requireFiles) {
        List<ProjectDocument> projectDocuments = projectDocumentRepository.findByProjectId(projectId);
        if (projectDocuments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }
        if (requireFiles) {
            loadStoredDocumentFiles(projectDocuments);
        }
        return projectDocuments;
    }

    private List<StoredDocumentFile> loadStoredDocumentFiles(List<ProjectDocument> projectDocuments) {
        List<StoredDocumentFile> storedFiles = new ArrayList<>();
        for (ProjectDocument projectDocument : projectDocuments) {
            storedFiles.add(toStoredDocumentFile(projectDocument));
        }
        return storedFiles;
    }

    private StoredDocumentFile toStoredDocumentFile(ProjectDocument projectDocument) {
        String storagePath = projectDocument.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }

        Path path = Path.of(storagePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }

        return new StoredDocumentFile(
                projectDocument.getOriginalFileName(),
                projectDocument.getContentType(),
                projectDocument.getFileSize(),
                path
        );
    }

    private ProjectDocument toProjectDocument(Project project, ProjectDocumentStatus status, StoredFile storedFile) {
        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(status);
        document.setOriginalFileName(storedFile.originalFileName());
        document.setStoredFileName(storedFile.storedFileName());
        document.setStoragePath(storedFile.path().toString());
        document.setExtension(storedFile.extension());
        document.setContentType(storedFile.contentType());
        document.setFileSize(storedFile.fileSize());
        return document;
    }

    private StoredFile storeValidatedFile(ValidatedUploadFile validatedFile) {
        Path rootDirectory = Path.of(documentStorageProperties.getStoragePath()).toAbsolutePath().normalize();
        String storedFileName = UUID.randomUUID() + "." + validatedFile.extension();
        Path targetPath = rootDirectory.resolve(storedFileName).normalize();
        if (!targetPath.startsWith(rootDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "Invalid file path.");
        }

        try {
            Files.createDirectories(rootDirectory);
            try (InputStream inputStream = validatedFile.file().getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    validatedFile.originalFileName(),
                    storedFileName,
                    validatedFile.extension(),
                    validatedFile.contentType(),
                    validatedFile.fileSize(),
                    targetPath
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT_DOCUMENT_SAVE_FAILED", "file upload failed", exception);
        }
    }

    private void deleteStoredFiles(List<ProjectDocument> documents) {
        List<Path> paths = documents.stream()
                .map(ProjectDocument::getStoragePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize())
                .toList();
        cleanupFiles(paths);
    }

    private void cleanupFiles(List<Path> savedPaths) {
        for (Path path : savedPaths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT_DOCUMENT_CLEANUP_FAILED", "Failed to clean up stored file.", exception);
            }
        }
    }

    private Project loadDraftProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        return project;
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "Invalid file name.");
        }

        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFC).replace("\\", "/");
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName) || baseName.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "Invalid file name.");
        }
        return baseName;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported file extension: " + fileName);
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension, String fileName) {
        if (!documentStorageProperties.getAllowedExtensions().contains(extension)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported file extension: " + fileName);
        }
    }

    private void validateMimeType(String contentType, String fileName) {
        if (contentType == null || !documentStorageProperties.getAllowedMimeTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported MIME type for file: " + fileName);
        }
    }

    private void validateFileSize(long fileSize, String fileName) {
        if (fileSize > documentStorageProperties.getMaxFileSize()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PROJECT_DOCUMENT_TOO_LARGE", "File size exceeds limit: " + fileName);
        }
    }

    record ValidatedUploadFile(
            MultipartFile file,
            String originalFileName,
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
            Path path
    ) {
    }
}
