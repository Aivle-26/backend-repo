package com.aivle26.aipm.Config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(S3Config.class)
@TestPropertySource(properties = {
        "app.s3.region=ap-northeast-2",
        "app.s3.bucket=aipm-test-bucket"
})
class S3ConfigTest {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Properties properties;

    @Test
    void createsS3ClientFromConfiguredRegionWithoutNetworkCall() {
        assertThat(s3Client).isNotNull();
        assertThat(s3Client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_2);
        assertThat(properties.getBucket()).isEqualTo("aipm-test-bucket");
    }
}
