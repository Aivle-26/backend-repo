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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "project_task_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_task_assignment_wbs",
                columnNames = {"project_id", "wbs_task_id"}
        )
)
public class ProjectTaskAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wbs_task_id", nullable = false)
    private ProjectWbsTask wbsTask;

    @Column(nullable = false, length = 50)
    private String employeeNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskProgressStatus status;

    @Column(nullable = false)
    private int progressRate;

    @Column(nullable = false)
    private LocalDate dueDate;

    // Nullable for backward compatibility with assignments created before this field existed.
    @Column(name = "assigned_hours")
    private Double assignedHours;

    @Column(name = "assigned_by", length = 50)
    private String assignedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        assignedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
