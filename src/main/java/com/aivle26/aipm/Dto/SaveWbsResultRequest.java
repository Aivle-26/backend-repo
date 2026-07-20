package com.aivle26.aipm.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveWbsResultRequest(
        @NotBlank
        @Size(max = 100)
        String agentExecutionId,

        @NotBlank
        @Size(max = 100)
        String agentVersion,

        @NotNull
        @Valid
        List<WbsTaskResultRequest> tasks
) {
}
