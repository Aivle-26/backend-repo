package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AgentRequestResult;
import com.aivle26.aipm.Entity.AgentExecutionStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StubProjectAgentClient implements ProjectAgentClient {
    @Override
    public AgentRequestResult requestDocumentAnalysis(Long projectId) {
        return new AgentRequestResult(
                "stub-analysis-" + UUID.randomUUID(),
                AgentExecutionStatus.REQUESTED,
                "stub-document-agent-v1"
        );
    }

    @Override
    public AgentRequestResult requestWbsGeneration(Long projectId) {
        return new AgentRequestResult(
                "stub-wbs-" + UUID.randomUUID(),
                AgentExecutionStatus.REQUESTED,
                "stub-wbs-agent-v1"
        );
    }

    @Override
    public AgentRequestResult requestScheduleGeneration(Long projectId) {
        return new AgentRequestResult(
                "stub-schedule-" + UUID.randomUUID(),
                AgentExecutionStatus.REQUESTED,
                "stub-schedule-agent-v1"
        );
    }
}
