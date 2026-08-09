package com.aivle26.aipm.Dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class KosaEffortEstimate {

    private KosaEffortEstimate() {
    }

    public record AiRequest(
            @JsonProperty("project_id") Long projectId,
            @JsonProperty("project_name") String projectName,
            @JsonProperty("wbs_tasks") List<WbsTask> wbsTasks
    ) {
        public record WbsTask(
                @JsonProperty("wbs_id") Long wbsId,
                @JsonProperty("wbs_name") String wbsName,
                String description,
                @JsonProperty("start_date") LocalDate startDate,
                @JsonProperty("end_date") LocalDate endDate
        ) {
        }
    }

    public record AiResponse(
            @JsonProperty("project_id") Long projectId,
            @JsonProperty("workdays_per_month") BigDecimal workdaysPerMonth,
            @JsonProperty("wbs_efforts") List<WbsEffort> wbsEfforts,
            @JsonProperty("job_efforts") List<JobEffort> jobEfforts,
            @JsonProperty("total_estimated_person_days") BigDecimal totalEstimatedPersonDays,
            @JsonProperty("total_estimated_mm") BigDecimal totalEstimatedMm,
            @JsonProperty("llm_status") String llmStatus
    ) {
        public record WbsEffort(
                @JsonProperty("wbs_id") Long wbsId,
                @JsonProperty("wbs_name") String wbsName,
                @JsonProperty("kosa_job_category") String kosaJobCategory,
                @JsonProperty("detailed_job") String detailedJob,
                @JsonProperty("estimated_person_days") BigDecimal estimatedPersonDays,
                @JsonProperty("estimated_mm") BigDecimal estimatedMm,
                @JsonProperty("estimation_reason") String estimationReason,
                BigDecimal confidence
        ) {
        }

        public record JobEffort(
                @JsonProperty("kosa_job_category") String kosaJobCategory,
                @JsonProperty("detailed_job") String detailedJob,
                @JsonProperty("estimated_person_days") BigDecimal estimatedPersonDays,
                @JsonProperty("estimated_mm") BigDecimal estimatedMm,
                @JsonProperty("wbs_ids") List<Long> wbsIds
        ) {
        }
    }

    public record Response(
            Long projectId,
            String projectName,
            LocalDate projectStartDate,
            LocalDate projectEndDate,
            int kosaRateYear,
            BigDecimal workdaysPerMonth,
            BigDecimal totalEstimatedPersonDays,
            BigDecimal totalEstimatedMm,
            long totalPersonnelAmount,
            String currency,
            String llmStatus,
            List<Personnel> personnel,
            List<WbsEvidence> wbsEfforts
    ) {
        public record Personnel(
                String employeeNumber,
                String employeeName,
                String kosaJobCategory,
                String detailedJob,
                BigDecimal estimatedPersonDays,
                BigDecimal estimatedMm,
                int headcount,
                BigDecimal durationMonths,
                BigDecimal utilizationRate,
                long standardMonthlyRate,
                long proposedMonthlyRate,
                long amount,
                List<WbsEvidence> wbsEvidence
        ) {
        }

        public record WbsEvidence(
                Long wbsId,
                String wbsName,
                String employeeNumber,
                String employeeName,
                String kosaJobCategory,
                String detailedJob,
                BigDecimal estimatedPersonDays,
                BigDecimal estimatedMm,
                String estimationReason,
                BigDecimal confidence
        ) {
        }
    }
}
