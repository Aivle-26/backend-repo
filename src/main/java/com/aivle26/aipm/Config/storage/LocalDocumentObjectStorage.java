package com.aivle26.aipm.Config.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.document", name = "storage-type", havingValue = "local")
public class LocalDocumentObjectStorage implements DocumentObjectStorage {
    private final DocumentStorageProperties properties;

    @Override
    public void put(String objectKey, String contentType, long contentLength, InputStream inputStream) {
        Path target = resolveObjectPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write local project document.", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            return Files.readAllBytes(resolveObjectPath(objectKey));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read local project document.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolveObjectPath(objectKey));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete local project document.", exception);
        }
    }

    private Path resolveObjectPath(String objectKey) {
        Path root = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Document object key escapes the configured storage path.");
        }
        return target;
    }
}
