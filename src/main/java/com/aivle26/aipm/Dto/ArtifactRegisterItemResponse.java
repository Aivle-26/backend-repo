package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ArtifactCheckStatus;
import com.aivle26.aipm.Entity.ProjectArtifactType;

public record ArtifactRegisterItemResponse(
        ProjectArtifactType artifactType,
        String artifactName,
        ArtifactCheckStatus status,
        String version,
        String requiredVersion,
        ArtifactApprovalStatus approvalStatus
) {
}
