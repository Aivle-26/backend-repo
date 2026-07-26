package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.AiServerDocumentExtractClient;
import com.aivle26.aipm.client.ai.AiServerJsonResponse;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
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

    // 프로젝트의 저장 파일을 AI 추출 서버에 전달하고 상태 코드와 JSON 응답을 반환한다.
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

    // 프로젝트 문서 추출을 실행하고 응답 본문 JSON만 반환한다.
    public JsonNode extractDocumentsBody(Long projectId) {
        return extractStoredDocuments(projectId).body();
    }

    // 시작 나노 시각을 현재 시각과 비교해 경과 밀리초를 반환한다.
    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
