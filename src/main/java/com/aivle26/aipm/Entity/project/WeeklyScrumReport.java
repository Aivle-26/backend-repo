package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "weekly_scrum_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weekly_scrum_report_project_week",
                columnNames = {"project_id", "week_start_date"}
        )
)
public class WeeklyScrumReport {
    public enum WorkflowStatus {
        DRAFT_INPUT,
        SUMMARIZED,
        REVIEWED,
        ACTIONS_RECOMMENDED,
        PM_REVIEWING,
        FINALIZED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(length = 1000)
    private String sprintGoal;

    @Column(nullable = false)
    private boolean enableLlm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowStatus workflowStatus;

    @Lob
    private String summarizeResponseJson;

    @Lob
    private String reviewResponseJson;

    @Lob
    private String recommendResponseJson;

    @Lob
    private String pmReviewJson;

    @Lob
    private String finalizeResponseJson;

    @Lob
    private String finalReport;

    @Column(length = 30)
    private String summarizeLlmStatus;

    @Column(length = 30)
    private String reviewLlmStatus;

    @Column(length = 30)
    private String recommendLlmStatus;

    @Column(length = 30)
    private String finalizeLlmStatus;

    @Column(length = 100)
    private String lastRequestId;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 1000)
    private String failureMessage;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public WeeklyScrumReport(
            Project project,
            LocalDate weekStartDate,
            String sprintGoal,
            boolean enableLlm
    ) {
        this.project = project;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekStartDate.plusDays(6);
        this.sprintGoal = sprintGoal;
        this.enableLlm = enableLlm;
        this.workflowStatus = WorkflowStatus.DRAFT_INPUT;
    }

    public void restart(String sprintGoal, boolean enableLlm) {
        this.sprintGoal = sprintGoal;
        this.enableLlm = enableLlm;
        this.workflowStatus = WorkflowStatus.DRAFT_INPUT;
        this.summarizeResponseJson = null;
        this.reviewResponseJson = null;
        this.recommendResponseJson = null;
        this.pmReviewJson = null;
        this.finalizeResponseJson = null;
        this.finalReport = null;
        this.summarizeLlmStatus = null;
        this.reviewLlmStatus = null;
        this.recommendLlmStatus = null;
        this.finalizeLlmStatus = null;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void fail(String failureCode, String failureMessage) {
        this.workflowStatus = WorkflowStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
