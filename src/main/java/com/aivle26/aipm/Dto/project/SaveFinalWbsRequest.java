package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveFinalWbsRequest(
        @NotNull
        @Size(min = 1)
        @Valid
        List<WbsTaskResultRequest> tasks
) {
}
