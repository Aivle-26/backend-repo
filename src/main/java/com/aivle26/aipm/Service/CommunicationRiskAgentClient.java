package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiCommunicationRiskRequest;
import com.aivle26.aipm.Dto.AiCommunicationRiskResponse;

/** AI 서버의 커뮤니케이션 리스크 분석 호출. 테스트에서 갈아끼울 수 있도록 인터페이스로 둔다. */
public interface CommunicationRiskAgentClient {
    AiCommunicationRiskResponse analyze(AiCommunicationRiskRequest request);
}
