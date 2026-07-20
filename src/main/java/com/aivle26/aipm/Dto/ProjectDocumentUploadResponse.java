package com.aivle26.aipm.Dto;

import java.util.List;

public record ProjectDocumentUploadResponse(
        Long projectId,
        List<ProjectDocumentUploadItemResponse> documents
) {
}
