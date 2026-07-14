package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.AgentExecutionStatus;

public record AgentRequestResult(
        String agentExecutionId,
        AgentExecutionStatus status,
        String agentVersion
) {
}
