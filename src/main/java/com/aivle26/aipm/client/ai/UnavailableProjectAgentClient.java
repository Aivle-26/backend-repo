package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.AgentRequestResult;
import com.aivle26.aipm.Exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UnavailableProjectAgentClient implements ProjectAgentClient {
    @Override
    public AgentRequestResult requestDocumentAnalysis(Long projectId) {
        throw unavailable();
    }

    @Override
    public AgentRequestResult requestWbsGeneration(Long projectId) {
        throw unavailable();
    }

    @Override
    public AgentRequestResult requestScheduleGeneration(Long projectId) {
        throw unavailable();
    }

    // 프로젝트 AI 기능이 연결되지 않았을 때 사용할 서비스 불가 예외를 생성한다.
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLANNING_AGENT_UNAVAILABLE", "문서 분석 서버에 연결할 수 없습니다.");
    }
}
