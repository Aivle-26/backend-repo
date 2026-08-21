package com.aivle26.aipm.Config.storage;

import com.aivle26.aipm.Config.S3Config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig({
        S3Config.class,
        LocalDocumentStorageContextTest.LocalStorageConfiguration.class,
        LocalDocumentObjectStorage.class
})
@TestPropertySource(properties = {
        "app.document.storage-type=local",
        "app.document.storage-path=build/test-local-storage",
        "app.document.max-file-size=1024",
        "app.document.allowed-extensions[0]=txt",
        "app.document.allowed-mime-types[0]=text/plain"
})
class LocalDocumentStorageContextTest {
    @Configuration
    @EnableConfigurationProperties(DocumentStorageProperties.class)
    static class LocalStorageConfiguration {
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DocumentObjectStorage documentObjectStorage;

    @Test
    void localModeStartsWithoutS3Configuration() {
        assertThat(documentObjectStorage).isInstanceOf(LocalDocumentObjectStorage.class);
        assertThat(applicationContext.getBeansOfType(S3Client.class)).isEmpty();
    }
}
