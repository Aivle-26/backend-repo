package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Config.S3Properties;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectDocumentService {
    private static final int MAX_FILE_COUNT = 10;
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;
    private static final int MAX_SANITIZED_FILE_NAME_LENGTH = 200;
    private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.of(
            "pdf", Set.of("application/pdf"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            "txt", Set.of("text/plain")
    );

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final DocumentStorageProperties documentStorageProperties;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    // 초안 프로젝트의 업로드 파일을 검증·교체 저장하고 문서 메타데이터를 반환한다.
    @Transactional
    public ProjectDocumentUploadResponse uploadInitialDocuments(Long projectId, List<MultipartFile> files) {
        Project project = loadDraftProject(projectId);
        projectAuthorizationService.requireProjectPm(projectId);
        List<ProjectDocument> savedDocuments = replaceProjectDocuments(project, validateUploadFiles(files), ProjectDocumentStatus.UPLOADED);
        return buildUploadResponse(project.getId(), savedDocuments);
    }

    // 프로젝트 문서 레코드와 실제 파일을 검증해 AI 전송용 저장 파일 목록을 반환한다.
    @Transactional(readOnly = true)
    public List<StoredDocumentFile> getStoredDocumentFiles(Long projectId) {
        return loadStoredDocumentFiles(loadProjectDocuments(projectId, true));
    }

    // Selects only the requested documents after validating project ownership and scope.
    @Transactional(readOnly = true)
    public List<ProjectDocument> getAnalyzableProjectDocuments(
            Long projectId,
            List<Long> documentIds
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        if (documentIds == null || documentIds.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROJECT_DOCUMENT_REQUIRED",
                    "At least one project document is required."
            );
        }

        List<ProjectDocument> documents =
                projectDocumentRepository.findByProjectIdAndIdInOrderByIdAsc(projectId, documentIds);
        if (documents.size() != documentIds.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROJECT_DOCUMENT_SELECTION",
                    "Every document must belong to the requested project."
            );
        }
        if (documents.stream()
                .anyMatch(document -> document.getStatus() == ProjectDocumentStatus.ANALYZING)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROJECT_DOCUMENT_ANALYSIS_IN_PROGRESS",
                    "One or more selected documents are already being analyzed."
            );
        }
        return documents;
    }

    // Downloads exactly the validated document set for a planning-agent request.
    public List<StoredDocumentFile> getStoredDocumentFiles(List<ProjectDocument> documents) {
        return loadStoredDocumentFiles(documents);
    }

    // 프로젝트에 연결된 문서 메타데이터를 조회해 엔티티 목록으로 반환한다.
    @Transactional(readOnly = true)
    public List<ProjectDocument> getProjectDocuments(Long projectId) {
        return loadProjectDocuments(projectId, false);
    }

    // 접근 가능한 프로젝트의 문서 메타데이터를 업로드 복원용 응답으로 반환한다.
    @Transactional(readOnly = true)
    public ProjectDocumentUploadResponse listProjectDocuments(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        return buildUploadResponse(
                projectId,
                projectDocumentRepository.findByProjectIdOrderByCreatedAtAscIdAsc(projectId)
        );
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
        List<String> createdObjectKeys = new ArrayList<>();

        try {
            for (ValidatedUploadFile validatedFile : validatedFiles) {
                projectDocumentRepository.findByProjectIdAndOriginalFileName(project.getId(), validatedFile.originalFileName())
                        .ifPresent(replacedDocuments::add);
            }

            for (ValidatedUploadFile validatedFile : validatedFiles) {
                StoredFile storedFile = storeValidatedFile(project.getId(), validatedFile);
                createdObjectKeys.add(storedFile.objectKey());
                createdDocuments.add(toProjectDocument(project, status, storedFile));
            }

            deleteStoredFiles(replacedDocuments);
            if (!replacedDocuments.isEmpty()) {
                projectDocumentRepository.deleteAll(replacedDocuments);
                projectDocumentRepository.flush();
            }

            return projectDocumentRepository.saveAll(createdDocuments);
        } catch (RuntimeException exception) {
            cleanupObjects(createdObjectKeys);
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

        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(storagePath)
                    .build());
            return new StoredDocumentFile(
                    projectDocument.getOriginalFileName(),
                    projectDocument.getContentType(),
                    projectDocument.getFileSize(),
                    object.asByteArray()
            );
        } catch (SdkException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DOCUMENT_STORAGE_ERROR", "file download failed", exception);
        }
    }

    // 저장된 파일 정보와 프로젝트를 문서 메타데이터 엔티티로 변환한다.
    private ProjectDocument toProjectDocument(Project project, ProjectDocumentStatus status, StoredFile storedFile) {
        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(status);
        document.setOriginalFileName(storedFile.originalFileName());
        document.setStoredFileName(storedFile.storedFileName());
        document.setStoragePath(storedFile.objectKey());
        document.setExtension(storedFile.extension());
        document.setContentType(storedFile.contentType());
        document.setFileSize(storedFile.fileSize());
        return document;
    }

    // 검증된 업로드 파일에 안전한 UUID 이름을 부여해 최종 저장소에 기록한다.
    private StoredFile storeValidatedFile(Long projectId, ValidatedUploadFile validatedFile) {
        UUID uploadId = UUID.randomUUID();
        String storedFileName = uploadId + "." + validatedFile.extension();
        String objectKey = "projects/%d/documents/%s/%s".formatted(
                projectId,
                uploadId,
                sanitizeFileName(validatedFile.originalFileName(), validatedFile.extension())
        );
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(validatedFile.contentType())
                .contentLength(validatedFile.fileSize())
                .build();

        try (InputStream inputStream = validatedFile.file().getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, validatedFile.fileSize()));
            return new StoredFile(
                    validatedFile.originalFileName(),
                    storedFileName,
                    validatedFile.extension(),
                    validatedFile.contentType(),
                    validatedFile.fileSize(),
                    objectKey
            );
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DOCUMENT_STORAGE_ERROR", "file upload failed", exception);
        }
    }

    // 문서 엔티티의 유효한 저장 경로를 추출해 실제 파일 정리로 전달한다.
    private void deleteStoredFiles(List<ProjectDocument> documents) {
        List<String> objectKeys = documents.stream()
                .map(ProjectDocument::getStoragePath)
                .filter(path -> path != null && !path.isBlank())
                .toList();
        cleanupObjects(objectKeys);
    }

    // 전달된 S3 객체 키를 삭제하고 정리 실패는 로그로 남긴다.
    private void cleanupObjects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(objectKey)
                        .build());
            } catch (RuntimeException exception) {
                log.warn("Failed to delete S3 project document: objectKey={}", objectKey, exception);
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
        if (baseName.isBlank()
                || baseName.length() > MAX_ORIGINAL_FILE_NAME_LENGTH
                || ".".equals(baseName)
                || "..".equals(baseName)
                || baseName.contains("..")) {
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
        if (!documentStorageProperties.getAllowedExtensions().contains(extension)
                || !MIME_TYPES_BY_EXTENSION.containsKey(extension)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported file extension: " + fileName);
        }
    }

    // 파일 MIME 유형이 설정된 업로드 허용 목록에 포함되는지 검증한다.
    private void validateMimeType(String contentType, String fileName) {
        String normalizedContentType = normalizeContentType(contentType);
        String extension = extractExtension(fileName);
        if (!documentStorageProperties.getAllowedMimeTypes().contains(normalizedContentType)
                || !MIME_TYPES_BY_EXTENSION.get(extension).contains(normalizedContentType)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported MIME type for file: " + fileName);
        }
    }

    // 업로드 파일 크기가 설정된 최대 허용 크기를 넘지 않는지 검증한다.
    private void validateFileSize(long fileSize, String fileName) {
        if (fileSize > documentStorageProperties.getMaxFileSize()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PROJECT_DOCUMENT_TOO_LARGE", "File size exceeds limit: " + fileName);
        }
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
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_PROJECT_DOCUMENT", "Unsupported MIME type.");
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
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
            String objectKey
    ) {
    }
}
