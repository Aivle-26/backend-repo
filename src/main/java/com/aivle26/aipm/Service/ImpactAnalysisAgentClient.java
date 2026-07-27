package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiImpactAnalysisRequest;
import com.aivle26.aipm.Dto.AiImpactAnalysisResponse;

/** AI 서버의 요구사항 변경 영향도 평가 호출. 테스트에서 갈아끼울 수 있도록 인터페이스로 둔다. */
public interface ImpactAnalysisAgentClient {
    AiImpactAnalysisResponse assess(AiImpactAnalysisRequest request);
}
