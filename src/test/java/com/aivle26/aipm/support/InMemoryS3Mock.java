package com.aivle26.aipm.support;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

public final class InMemoryS3Mock {
    private InMemoryS3Mock() {
    }

    public static Store configure(S3Client s3Client) {
        Store store = new Store();

        lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    PutObjectRequest request = invocation.getArgument(0);
                    RequestBody body = invocation.getArgument(1);
                    try (var input = body.contentStreamProvider().newStream()) {
                        store.put(request.key(), input.readAllBytes());
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                    return PutObjectResponse.builder().build();
                });

        lenient().when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    byte[] content = store.get(request.key());
                    if (content == null) {
                        throw NoSuchKeyException.builder().message("Missing test object").build();
                    }
                    return ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().contentLength((long) content.length).build(),
                            content
                    );
                });

        lenient().when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenAnswer(invocation -> {
                    DeleteObjectRequest request = invocation.getArgument(0);
                    store.remove(request.key());
                    return DeleteObjectResponse.builder().build();
                });

        return store;
    }

    public static final class Store {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        public void put(String key, byte[] content) {
            objects.put(key, content.clone());
        }

        public byte[] get(String key) {
            byte[] content = objects.get(key);
            return content == null ? null : content.clone();
        }

        public boolean contains(String key) {
            return objects.containsKey(key);
        }

        public void remove(String key) {
            objects.remove(key);
        }
    }
}
