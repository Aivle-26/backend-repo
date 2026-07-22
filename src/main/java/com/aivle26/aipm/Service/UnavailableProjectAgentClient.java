package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AgentRequestResult;
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

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLANNING_AGENT_UNAVAILABLE", "문서 분석 서버에 연결할 수 없습니다.");
    }
}
