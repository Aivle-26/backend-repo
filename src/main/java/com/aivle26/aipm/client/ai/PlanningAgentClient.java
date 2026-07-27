package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;

import java.util.List;

public interface PlanningAgentClient {
    // 저장 파일과 LLM 옵션으로 문서 추출을 요청해 구조화된 분석 결과를 반환한다.
    PlanningDocumentExtractResponse extractDocuments(List<StoredDocumentFile> files, boolean enableLlm);
}
