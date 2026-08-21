package com.aivle26.aipm.Dto.project;

import java.util.List;

public record ProjectDocumentUploadResponse(
        Long projectId,
        List<ProjectDocumentUploadItemResponse> documents
) {
}
