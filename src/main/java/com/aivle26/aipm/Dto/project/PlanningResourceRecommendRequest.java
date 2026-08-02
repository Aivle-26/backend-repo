package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record PlanningResourceRecommendRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("wbs_tasks") List<WbsTask> wbsTasks,
        @JsonProperty("project_members") List<ProjectMember> projectMembers
) {
    public record WbsTask(
            @JsonProperty("wbs_id") Long wbsId,
            @JsonProperty("wbs_name") String wbsName,
            String description,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate
    ) {
    }

    public record ProjectMember(
            @JsonProperty("project_member_id") Long projectMemberId,
            List<String> roles,
            List<Skill> skills,
            List<Allocation> allocations
    ) {
    }

    public record Skill(
            @JsonProperty("skill_code") String skillCode,
            @JsonProperty("proficiency_level") int proficiencyLevel,
            @JsonProperty("experience_months") int experienceMonths
    ) {
    }

    public record Allocation(
            @JsonProperty("allocation_start_date") LocalDate allocationStartDate,
            @JsonProperty("allocation_end_date") LocalDate allocationEndDate,
            @JsonProperty("available_hours_per_week") double availableHoursPerWeek,
            @JsonProperty("allocation_status") String allocationStatus
    ) {
    }
}
