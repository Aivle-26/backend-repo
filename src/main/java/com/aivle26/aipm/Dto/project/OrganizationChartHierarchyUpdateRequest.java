package com.aivle26.aipm.Dto.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrganizationChartHierarchyUpdateRequest(
        @NotBlank String baseVersion,
        @NotEmpty List<@Valid MemberHierarchy> members
) {
    public record MemberHierarchy(
            @NotBlank String memberId,
            String parentMemberId,
            @Min(0) int order
    ) {
    }
}
