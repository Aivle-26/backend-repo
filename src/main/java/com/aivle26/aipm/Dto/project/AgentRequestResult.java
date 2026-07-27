package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.AgentExecutionStatus;



public record AgentRequestResult(
        String agentExecutionId,
        AgentExecutionStatus status,
        String agentVersion
) {
}
