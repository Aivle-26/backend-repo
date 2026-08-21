package com.aivle26.aipm.Config.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDocumentObjectStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void storesReadsAndDeletesDocumentInsideConfiguredRoot() {
        DocumentStorageProperties properties = properties();
        LocalDocumentObjectStorage storage = new LocalDocumentObjectStorage(properties);
        byte[] content = "hello".getBytes();
        String objectKey = "projects/42/documents/id/requirements.txt";

        storage.put(objectKey, "text/plain", content.length, new ByteArrayInputStream(content));

        assertThat(storage.get(objectKey)).isEqualTo(content);
        assertThat(tempDirectory.resolve(objectKey)).exists();

        storage.delete(objectKey);
        assertThat(tempDirectory.resolve(objectKey)).doesNotExist();
    }

    @Test
    void rejectsObjectKeyOutsideConfiguredRoot() {
        LocalDocumentObjectStorage storage = new LocalDocumentObjectStorage(properties());

        assertThatThrownBy(() -> storage.put(
                "../../outside.txt",
                "text/plain",
                1,
                new ByteArrayInputStream(new byte[]{1})
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private DocumentStorageProperties properties() {
        DocumentStorageProperties properties = new DocumentStorageProperties();
        properties.setStoragePath(tempDirectory.toString());
        return properties;
    }
}
