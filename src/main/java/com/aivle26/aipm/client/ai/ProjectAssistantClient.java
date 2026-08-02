package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.DeliverableRagRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryResponse;

public interface ProjectAssistantClient {
    ProjectAssistantQueryResponse query(DeliverableRagRequest request);
}
