package com.aivle26.aipm.Config.storage;

import com.aivle26.aipm.Config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.document",
        name = "storage-type",
        havingValue = "s3",
        matchIfMissing = true
)
public class S3DocumentObjectStorage implements DocumentObjectStorage {
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Override
    public void put(String objectKey, String contentType, long contentLength, InputStream inputStream) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
    }

    @Override
    public byte[] get(String objectKey) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(objectKey)
                        .build())
                .asByteArray();
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .build());
    }
}
