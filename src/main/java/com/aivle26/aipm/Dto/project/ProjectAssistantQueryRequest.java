package com.aivle26.aipm.Dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectAssistantQueryRequest(
        @NotBlank @Size(max = 2000) String question,
        Boolean enableLlm
) {
    public boolean isLlmEnabled() {
        return enableLlm == null || enableLlm;
    }
}
