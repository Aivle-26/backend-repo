package com.aivle26.aipm.Config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {
    @NotBlank
    private String region = "ap-northeast-2";

    @NotBlank
    private String bucket;
}
