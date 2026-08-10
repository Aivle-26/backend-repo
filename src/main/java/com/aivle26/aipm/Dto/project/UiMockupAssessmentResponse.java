package com.aivle26.aipm.Dto.project;

import java.util.List;

public record UiMockupAssessmentResponse(
        Decision decision,
        String reason,
        List<Long> evidenceRequirementIds,
        List<String> candidateScreens
) {
    public enum Decision {
        REQUIRED,
        RECOMMENDED,
        NOT_NEEDED
    }
}
