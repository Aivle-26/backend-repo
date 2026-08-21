package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "project_schedules")
public class ProjectSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_result_id", nullable = false)
    private ProjectScheduleResult scheduleResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wbs_task_id", nullable = false)
    private ProjectWbsTask wbsTask;

    @Column(nullable = false, length = 100)
    private String externalScheduleId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int estimatedDays;

    @Column(nullable = false)
    private boolean milestone;

    @Column(nullable = false)
    private int bufferDays;

    @Column(nullable = false)
    private boolean confirmed;

    @ManyToMany
    @JoinTable(
            name = "project_schedule_predecessors",
            joinColumns = @JoinColumn(name = "schedule_id"),
            inverseJoinColumns = @JoinColumn(name = "predecessor_schedule_id")
    )
    private Set<ProjectSchedule> predecessors = new LinkedHashSet<>();

    @OneToMany(
            mappedBy = "projectSchedule",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("scenarioType ASC")
    private Set<ProjectScheduleScenario> scenarios = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 프로젝트 일정 최초 저장 시 생성 시각을 현재 시각으로 설정한다.
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void addScenario(ProjectScheduleScenario scenario) {
        scenario.setProjectSchedule(this);
        scenarios.add(scenario);
    }
}
