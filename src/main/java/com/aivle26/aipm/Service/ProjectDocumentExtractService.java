package com.aivle26.aipm.Service;

import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProjectDocumentExtractService {
    private static final Logger log = LoggerFactory.getLogger(ProjectDocumentExtractService.class);
    private static final int MAX_FILE_COUNT = 10;
    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "hwp", "hwpx", "docx", "txt", "md", "csv");

    private final AiServerDocumentExtractClient aiServerDocumentExtractClient;

    public ProjectDocumentExtractService(AiServerDocumentExtractClient aiServerDocumentExtractClient) {
        this.aiServerDocumentExtractClient = aiServerDocumentExtractClient;
    }

    public AiServerJsonResponse extractDocuments(Long projectId, List<MultipartFile> files) {
        validateFiles(files);
        List<String> fileNames = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .toList();

        long startedAt = System.nanoTime();
        log.info("AI server document relay started: projectId={}, fileCount={}, fileNames={}",
                projectId, files.size(), fileNames);

        try {
            AiServerJsonResponse response = aiServerDocumentExtractClient.extractDocuments(files);
            long elapsedMs = elapsedMillis(startedAt);
            log.info("AI server document relay finished: projectId={}, status={}, elapsedMs={}",
                    projectId, response.status().value(), elapsedMs);
            return response;
        } catch (ApiException exception) {
            long elapsedMs = elapsedMillis(startedAt);
            log.warn("AI server document relay failed: projectId={}, status={}, code={}, elapsedMs={}",
                    projectId,
                    exception.getStatus().value(),
                    exception.getCode(),
                    elapsedMs);
            throw exception;
        }
    }

    public JsonNode extractDocumentsBody(Long projectId, List<MultipartFile> files) {
        return extractDocuments(projectId, files).body();
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROJECT_DOCUMENT_REQUIRED",
                    "문서는 최소 1개 이상 업로드해야 합니다."
            );
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TOO_MANY_PROJECT_DOCUMENTS",
                    "문서는 최대 10개까지 업로드할 수 있습니다."
            );
        }
        for (MultipartFile file : files) {
            validateFile(file);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROJECT_DOCUMENT_EMPTY",
                    "빈 파일은 업로드할 수 없습니다."
            );
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROJECT_DOCUMENT",
                    "원본 파일명이 필요합니다."
            );
        }

        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 1 || extensionIndex == fileName.length() - 1) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNSUPPORTED_PROJECT_DOCUMENT",
                    "지원하지 않는 파일 형식입니다: " + fileName
            );
        }

        String extension = fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNSUPPORTED_PROJECT_DOCUMENT",
                    "지원하지 않는 파일 형식입니다: " + fileName
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PROJECT_DOCUMENT_TOO_LARGE",
                    "파일 크기는 20MB 이하여야 합니다: " + fileName
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
