package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifactType;

import java.time.LocalDateTime;

public record UiMockupArtifactResponse(
        Long artifactId,
        Long projectId,
        ProjectArtifactType artifactType,
        String artifactName,
        String version,
        ArtifactApprovalStatus approvalStatus,
        String contentType,
        long fileSize,
        LocalDateTime generatedAt,
        String previewUrl,
        String downloadUrl
) {
}
