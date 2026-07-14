package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AgentRequestResult;

public interface ProjectAgentClient {
    AgentRequestResult requestDocumentAnalysis(Long projectId);

    AgentRequestResult requestWbsGeneration(Long projectId);

    AgentRequestResult requestScheduleGeneration(Long projectId);
}
