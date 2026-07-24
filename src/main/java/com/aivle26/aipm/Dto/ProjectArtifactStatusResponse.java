package com.aivle26.aipm.Dto;

import java.util.List;

public record ProjectArtifactStatusResponse(
        Long projectId,
        int totalRequiredCount,
        int registeredCount,
        int approvedCount,
        double registrationRate,
        double approvalCompletionRate,
        List<ArtifactRegisterItemResponse> artifactRegister,
        List<ArtifactRegisterItemResponse> missingArtifacts,
        List<ArtifactRegisterItemResponse> unapprovedArtifacts,
        List<ArtifactRegisterItemResponse> outdatedArtifacts,
        List<String> recommendations
) {
}
