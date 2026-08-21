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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "project_wbs_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_wbs_results_project",
                columnNames = "project_id"
        )
)
public class ProjectWbsResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, unique = true, length = 100)
    private String agentExecutionId;

    @Column(nullable = false, length = 100)
    private String agentVersion;

    @Column(columnDefinition = "TEXT")
    private String initialTasksJson;

    @Column(length = 30)
    private String llmStatus;

    @Column(length = 30)
    private String generationStatus;

    @Column(columnDefinition = "TEXT")
    private String warningsJson;

    @Column(columnDefinition = "TEXT")
    private String requirementCoverageJson;

    @Column(columnDefinition = "TEXT")
    private String artifactCoverageJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // WBS 생성 결과 최초 저장 시 생성 시각을 현재 시각으로 설정한다.
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
