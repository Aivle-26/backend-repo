package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiReassignmentRequest;
import com.aivle26.aipm.Dto.AiReassignmentResponse;

/** AI 서버의 담당자 재배정 추천 호출. 테스트에서 갈아끼울 수 있도록 인터페이스로 둔다. */
public interface ReassignmentAgentClient {
    AiReassignmentResponse recommend(AiReassignmentRequest request);
}
