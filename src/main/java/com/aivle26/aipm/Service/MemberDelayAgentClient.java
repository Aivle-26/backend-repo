package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiMemberDelayRequest;
import com.aivle26.aipm.Dto.AiMemberDelayResponse;

/** AI 서버의 팀원별 업무 지연 분석 호출. 테스트에서 갈아끼울 수 있도록 인터페이스로 둔다. */
public interface MemberDelayAgentClient {
    AiMemberDelayResponse analyze(AiMemberDelayRequest request);
}
