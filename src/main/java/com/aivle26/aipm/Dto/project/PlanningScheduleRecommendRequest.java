package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningScheduleRecommendRequest(
        @JsonProperty("project_id")
        Long projectId,

        @JsonProperty("project_start_date")
        LocalDate projectStartDate,

        @JsonProperty("target_end_date")
        LocalDate targetEndDate,

        @JsonProperty("wbs_items")
        List<ScheduleWbsItem> wbsItems
) {
    public record ScheduleWbsItem(
            @JsonProperty("wbs_id")
            Long wbsId,

            @JsonProperty("wbs_code")
            String wbsCode,

            @JsonProperty("parent_wbs_id")
            Long parentWbsId,

            @JsonProperty("item_type")
            ItemType itemType,

            @JsonProperty("wbs_name")
            String wbsName,

            String description
    ) {
    }

    public enum ItemType {
        PHASE,
        WORK_PACKAGE,
        TASK
    }
}
