package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;



public record ProjectDocumentUploadItemResponse(
        Long documentId,
        String originalFileName,
        ProjectDocumentStatus status,
        long fileSize
) {
}
