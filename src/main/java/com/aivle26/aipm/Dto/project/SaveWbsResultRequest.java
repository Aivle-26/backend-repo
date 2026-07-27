package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveWbsResultRequest(
        @JsonAlias("agent_execution_id")
        @NotBlank
        @Size(max = 100)
        String agentExecutionId,

        @JsonAlias("agent_version")
        @NotBlank
        @Size(max = 100)
        String agentVersion,

        @NotNull
        @Size(min = 1)
        @Valid
        List<WbsTaskResultRequest> tasks
) {
}
