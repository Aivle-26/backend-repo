package com.aivle26.aipm.Config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.document")
public class DocumentStorageProperties {
    @NotBlank
    private String storagePath;

    @Min(1)
    private long maxFileSize;

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>();

    @NotEmpty
    private List<String> allowedMimeTypes = new ArrayList<>();
}
