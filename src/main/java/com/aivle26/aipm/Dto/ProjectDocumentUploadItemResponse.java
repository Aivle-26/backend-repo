package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.ProjectDocumentStatus;

public record ProjectDocumentUploadItemResponse(
        Long documentId,
        String originalFileName,
        ProjectDocumentStatus status,
        long fileSize
) {
}
