package com.aivle26.aipm.Dto.project;

import java.util.List;

public record OrganizationChartHierarchyResponse(
        Long projectId,
        Long artifactId,
        String version,
        String projectManagerMemberId,
        List<MemberNode> members
) {
    public record MemberNode(
            String memberId,
            String parentMemberId,
            String memberName,
            String projectJobFamily,
            int order,
            boolean capabilityRegistered
    ) {
    }
}
