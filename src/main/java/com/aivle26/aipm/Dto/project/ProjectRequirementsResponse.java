package com.aivle26.aipm.Dto.project;

import java.util.List;

public record ProjectRequirementsResponse(
        Long projectId,
        List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> aiSuggestions,
        List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> finalRequirements
) {
}
