package com.aivle26.aipm.Config.storage;

import java.io.InputStream;

public interface DocumentObjectStorage {
    void put(String objectKey, String contentType, long contentLength, InputStream inputStream);

    byte[] get(String objectKey);

    void delete(String objectKey);
}
