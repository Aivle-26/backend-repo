package com.aivle26.aipm.Dto.project;

import java.time.LocalDate;
import java.util.List;

public record MissingWeeklyScrumMembersResponse(
        Long projectId,
        LocalDate weekStartDate,
        int totalMemberCount,
        int submittedMemberCount,
        List<String> missingEmployeeNumbers
) {
}
