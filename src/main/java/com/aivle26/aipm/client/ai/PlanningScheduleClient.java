package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.PlanningScheduleRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningScheduleRecommendResponse;

public interface PlanningScheduleClient {
    PlanningScheduleRecommendResponse recommendSchedules(PlanningScheduleRecommendRequest request);
}
