package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiSecurityCheckRequest;
import com.aivle26.aipm.Dto.AiSecurityCheckResponse;

/** AI 서버의 산출물 보안 검사 호출. 테스트에서 갈아끼울 수 있도록 인터페이스로 둔다. */
public interface SecurityCheckAgentClient {
    AiSecurityCheckResponse inspect(AiSecurityCheckRequest request);
}
