package com.aivle26.aipm.Dto.project;

import java.time.LocalDate;
import java.util.List;

public record ProjectScheduleResponse(
        Long scheduleResultId,
        Long projectId,
        String agentExecutionId,
        String agentVersion,
        String llmStatus,
        LocalDate projectStartDate,
        LocalDate targetEndDate,
        List<ScheduleDetail> schedules,
        List<String> warnings
) {
    public record ScheduleDetail(
            Long scheduleId,
            Long wbsId,
            String wbsCode,
            String wbsName,
            String wbsDescription,
            Long parentWbsId,
            PlanningScheduleRecommendRequest.ItemType itemType,
            int orderIndex,
            ScheduleDateRange expected,
            ScheduleDateRange recommended,
            ScheduleDateRange conservative,
            List<Long> predecessorWbsIds,
            boolean milestone,
            int bufferDays,
            boolean confirmed
    ) {
    }

    public record ScheduleDateRange(
            LocalDate startDate,
            LocalDate endDate,
            int estimatedDays
    ) {
    }
}
