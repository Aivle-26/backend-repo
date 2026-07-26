package com.aivle26.aipm.client.ai;

import java.nio.file.Path;

public record StoredDocumentFile(
        String originalFileName,
        String contentType,
        long fileSize,
        Path path
) {
}
