package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "weekly_scrum_submissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weekly_scrum_project_employee_week",
                columnNames = {"project_id", "employee_number", "week_start_date"}
        )
)
public class WeeklyScrumSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "employee_number", nullable = false, length = 50)
    private String employeeNumber;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false, length = 4000)
    private String completedWork;

    @Column(nullable = false, length = 4000)
    private String plannedWork;

    @Column(length = 4000)
    private String blockers;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public WeeklyScrumSubmission(Project project, String employeeNumber, LocalDate weekStartDate,
                                 String completedWork, String plannedWork, String blockers) {
        this.project = project;
        this.employeeNumber = employeeNumber;
        this.weekStartDate = weekStartDate;
        update(completedWork, plannedWork, blockers);
    }

    public void update(String completedWork, String plannedWork, String blockers) {
        this.completedWork = completedWork;
        this.plannedWork = plannedWork;
        this.blockers = blockers;
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
