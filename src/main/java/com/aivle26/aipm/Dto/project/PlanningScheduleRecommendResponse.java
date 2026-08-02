package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningScheduleRecommendResponse(
        @JsonProperty("project_id")
        Long projectId,

        @JsonProperty("wbs_schedules")
        List<WbsSchedule> wbsSchedules,

        List<String> warnings,

        @JsonProperty("llm_status")
        String llmStatus,

        @JsonProperty("agent_execution_id")
        String agentExecutionId,

        @JsonProperty("agent_version")
        String agentVersion
) {
    public PlanningScheduleRecommendResponse(
            Long projectId,
            List<WbsSchedule> wbsSchedules,
            List<String> warnings,
            String agentExecutionId,
            String agentVersion
    ) {
        this(projectId, wbsSchedules, warnings, null, agentExecutionId, agentVersion);
    }

    public record WbsSchedule(
            @JsonProperty("wbs_id")
            Long wbsId,

            ScheduleDateRange expected,

            ScheduleDateRange recommended,

            ScheduleDateRange conservative,

            @JsonProperty("predecessor_wbs_ids")
            List<Long> predecessorWbsIds,

            Boolean milestone,

            @JsonProperty("buffer_days")
            Integer bufferDays,

            @JsonProperty("external_schedule_id")
            String externalScheduleId
    ) {
    }

    public record ScheduleDateRange(
            @JsonProperty("start_date")
            LocalDate startDate,

            @JsonProperty("end_date")
            LocalDate endDate
    ) {
    }
}
