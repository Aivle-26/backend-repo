package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.OrganizationChartGenerateRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendResponse;

public interface PlanningResourceClient {
    PlanningResourceRecommendResponse recommendAssignments(
            PlanningResourceRecommendRequest request
    );

    GeneratedOrganizationChart generateOrganizationChart(
            OrganizationChartGenerateRequest request
    );
}
