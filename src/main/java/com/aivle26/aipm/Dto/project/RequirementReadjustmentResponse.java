package com.aivle26.aipm.Dto.project;

import java.util.List;

public record RequirementReadjustmentResponse(
        Long projectId,
        List<RequirementChangeCandidateResponse> changeCandidates
) {
}
