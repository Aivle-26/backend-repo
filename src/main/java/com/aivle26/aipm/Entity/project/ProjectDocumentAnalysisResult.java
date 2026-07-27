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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "project_document_analysis_results")
public class ProjectDocumentAnalysisResult {

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

    @Column(nullable = false, length = 500)
    private String projectGoal;

    @Column(nullable = false, length = 1000)
    private String scope;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String deliverablesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String milestonesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String technologyStacksJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String constraintsJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String risksJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 문서 분석 결과 최초 저장 시 생성 시각을 현재 시각으로 설정한다.
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
