package com.aivle26.aipm.client.ai;

public record StoredDocumentFile(
        String originalFileName,
        String contentType,
        long fileSize,
        byte[] content
) {
}
