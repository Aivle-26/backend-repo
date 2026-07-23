package com.aivle26.aipm.Service;

import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProjectDocumentExtractService {
    private static final Logger log = LoggerFactory.getLogger(ProjectDocumentExtractService.class);

    private final ProjectDocumentService projectDocumentService;
    private final AiServerDocumentExtractClient aiServerDocumentExtractClient;

    public ProjectDocumentExtractService(
            ProjectDocumentService projectDocumentService,
            AiServerDocumentExtractClient aiServerDocumentExtractClient
    ) {
        this.projectDocumentService = projectDocumentService;
        this.aiServerDocumentExtractClient = aiServerDocumentExtractClient;
    }

    public AiServerJsonResponse extractStoredDocuments(Long projectId) {
        List<StoredDocumentFile> storedFiles = projectDocumentService.getStoredDocumentFiles(projectId);
        List<String> fileNames = storedFiles.stream()
                .map(StoredDocumentFile::originalFileName)
                .toList();

        long startedAt = System.nanoTime();
        log.info("AI server document analysis started: projectId={}, fileCount={}, fileNames={}",
                projectId, storedFiles.size(), fileNames);

        try {
            AiServerJsonResponse response = aiServerDocumentExtractClient.extractDocuments(storedFiles);
            long elapsedMs = elapsedMillis(startedAt);
            log.info("AI server document analysis finished: projectId={}, status={}, elapsedMs={}",
                    projectId, response.status().value(), elapsedMs);
            return response;
        } catch (ApiException exception) {
            long elapsedMs = elapsedMillis(startedAt);
            log.warn("AI server document analysis failed: projectId={}, status={}, code={}, elapsedMs={}",
                    projectId,
                    exception.getStatus().value(),
                    exception.getCode(),
                    elapsedMs);
            throw exception;
        }
    }

    public JsonNode extractDocumentsBody(Long projectId) {
        return extractStoredDocuments(projectId).body();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
