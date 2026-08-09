package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.PlanningCostEstimateRequest;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateResponse;
import com.aivle26.aipm.Dto.project.KosaEffortEstimate;

public interface PlanningCostClient {
    PlanningCostEstimateResponse estimate(PlanningCostEstimateRequest request);

    KosaEffortEstimate.AiResponse estimateEffort(
            KosaEffortEstimate.AiRequest request,
            String requestId
    );
}
