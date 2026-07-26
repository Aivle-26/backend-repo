package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Config.storage.DocumentStorageProperties;
import com.aivle26.aipm.Dto.project.ProjectDocumentUploadItemResponse;
import com.aivle26.aipm.Dto.project.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectDocumentService {
    private static final int MAX_FILE_COUNT = 10;

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentStorageProperties documentStorageProperties;

    // 초안 프로젝트의 업로드 파일을 검증·교체 저장하고 문서 메타데이터를 반환한다.
    @Transactional
    public ProjectDocumentUploadResponse uploadInitialDocuments(Long projectId, List<MultipartFile> files) {
        Project project = loadDraftProject(projectId);
        List<ProjectDocument> savedDocuments = replaceProjectDocuments(project, validateUploadFiles(files), ProjectDocumentStatus.UPLOADED);
        return buildUploadResponse(project.getId(), savedDocuments);
    }

    // 프로젝트 문서 레코드와 실제 파일을 검증해 AI 전송용 저장 파일 목록을 반환한다.
    @Transactional(readOnly = true)
    public List<StoredDocumentFile> getStoredDocumentFiles(Long projectId) {
        return loadStoredDocumentFiles(loadProjectDocuments(projectId, true));
    }

    // 프로젝트에 연결된 문서 메타데이터를 조회해 엔티티 목록으로 반환한다.
    @Transactional(readOnly = true)
    public List<ProjectDocument> getProjectDocuments(Long projectId) {
        return loadProjectDocuments(projectId, false);
    }

    // 전체 문서를 프로젝트별로 묶어 업로드 복원용 응답 목록으로 반환한다.
    @Transactional(readOnly = true)
    public List<ProjectDocumentUploadResponse> listProjectDocuments() {
        Map<Long, List<ProjectDocument>> documentsByProject = new LinkedHashMap<>();
        for (ProjectDocument document : projectDocumentRepository.findAllWithProjectOrderByProjectIdAndCreatedAt()) {
            documentsByProject
                    .computeIfAbsent(document.getProject().getId(), ignored -> new ArrayList<>())
                    .add(document);
        }

        return documentsByProject.entrySet().stream()
                .map(entry -> buildUploadResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    // 프로젝트에 연결된 문서 DB 레코드를 일괄 삭제한다.
    @Transactional
    public void deleteProjectDocumentRecords(Long projectId) {
        projectDocumentRepository.deleteAllByProjectId(projectId);
    }

    // 프로젝트 문서의 저장 경로를 조회해 실제 파일만 안전하게 삭제한다.
    @Transactional
    public void deleteProjectDocumentFiles(Long projectId) {
        List<ProjectDocument> documents = projectDocumentRepository.findByProjectId(projectId);
        if (!documents.isEmpty()) {
            deleteStoredFiles(documents);
        }
    }

    // 업로드 개수·중복명·확장자·MIME·크기를 검증해 저장 가능한 파일 목록을 반환한다.
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

    // 같은 프로젝트의 동일 파일명 문서를 제거하고 새 파일과 메타데이터로 교체한다.
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

    // 전달된 문서 레코드의 저장 경로를 기준으로 실제 파일을 정리한다.
    void cleanupStoredFiles(List<ProjectDocument> documents) {
        deleteStoredFiles(documents);
    }

    // 저장 문서 엔티티 목록을 프로젝트별 업로드 결과 DTO로 조립한다.
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

    // 프로젝트 문서를 조회하고 필요 시 실제 파일 존재까지 검증해 반환한다.
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

    // 문서 메타데이터 목록을 실제 파일이 확인된 AI 전송용 파일 목록으로 변환한다.
    private List<StoredDocumentFile> loadStoredDocumentFiles(List<ProjectDocument> projectDocuments) {
        List<StoredDocumentFile> storedFiles = new ArrayList<>();
        for (ProjectDocument projectDocument : projectDocuments) {
            storedFiles.add(toStoredDocumentFile(projectDocument));
        }
        return storedFiles;
    }

    // 문서 저장 경로의 소유 범위와 존재 여부를 검증해 저장 파일 객체로 반환한다.
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

    // 저장된 파일 정보와 프로젝트를 문서 메타데이터 엔티티로 변환한다.
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

    // 검증된 업로드 파일에 안전한 UUID 이름을 부여해 최종 저장소에 기록한다.
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

    // 문서 엔티티의 유효한 저장 경로를 추출해 실제 파일 정리로 전달한다.
    private void deleteStoredFiles(List<ProjectDocument> documents) {
        List<Path> paths = documents.stream()
                .map(ProjectDocument::getStoragePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize())
                .toList();
        cleanupFiles(paths);
    }

    // 전달된 저장 경로의 파일을 삭제하고 예상하지 못한 실패를 예외로 전환한다.
    private void cleanupFiles(List<Path> savedPaths) {
        for (Path path : savedPaths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT_DOCUMENT_CLEANUP_FAILED", "Failed to clean up stored file.", exception);
            }
        }
    }

    // 프로젝트 ID로 DRAFT 상태 프로젝트를 조회·검증해 반환한다.
    private Project loadDraftProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        return project;
    }

    // 업로드 파일명에서 경로를 제거하고 NFC 형식의 안전한 기본 파일명을 반환한다.
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

    // 정규화된 파일명에서 소문자 확장자를 추출하고 누락 시 요청을 거부한다.
    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported file extension: " + fileName);
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    // 파일 확장자가 설정된 업로드 허용 목록에 포함되는지 검증한다.
    private void validateExtension(String extension, String fileName) {
        if (!documentStorageProperties.getAllowedExtensions().contains(extension)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported file extension: " + fileName);
        }
    }

    // 파일 MIME 유형이 설정된 업로드 허용 목록에 포함되는지 검증한다.
    private void validateMimeType(String contentType, String fileName) {
        if (contentType == null || !documentStorageProperties.getAllowedMimeTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported MIME type for file: " + fileName);
        }
    }

    // 업로드 파일 크기가 설정된 최대 허용 크기를 넘지 않는지 검증한다.
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
