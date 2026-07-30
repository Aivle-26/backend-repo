package com.aivle26.aipm.client.ai;

public record StoredDocumentFile(
        String originalFileName,
        String contentType,
        long fileSize,
        byte[] content,
        Long documentId
) {
    public StoredDocumentFile(
            String originalFileName,
            String contentType,
            long fileSize,
            byte[] content
    ) {
        this(originalFileName, contentType, fileSize, content, null);
    }
}
