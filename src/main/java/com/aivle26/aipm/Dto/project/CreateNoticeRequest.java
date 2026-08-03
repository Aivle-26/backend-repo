package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String content
) {
}
