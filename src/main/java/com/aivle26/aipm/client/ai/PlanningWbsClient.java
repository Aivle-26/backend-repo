package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationResponse;

public interface PlanningWbsClient {
    // 확정 요구사항을 AI Server에 전달하고 WBS 결과만 반환받는다.
    PlanningWbsGenerationResponse generateWbs(PlanningWbsGenerationRequest request);
}
