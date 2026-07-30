package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "project_requirements")
public class ProjectRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id")
    private ProjectDocumentAnalysisResult analysisResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false)
    private ProjectDocument sourceDocument;

    @Column(nullable = false)
    private Long externalReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 2000)
    private String acceptanceCriteria;

    private LocalDate dueDate;

    @Column(length = 255)
    private String deliverableName;

    @Column(length = 1000)
    private String securityCondition;

    @Column(length = 255)
    private String sourceDocumentName;

    @Column(columnDefinition = "TEXT")
    private String sourceExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementStatus status;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(columnDefinition = "TEXT")
    private String aiSuggestionJson;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean includedInFinal = true;

    @OneToMany(
            mappedBy = "requirement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("id ASC")
    private List<ProjectRequirementEvidence> evidences = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 요구사항 최초 저장 시 생성·수정 시각을 현재 시각으로 설정한다.
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 요구사항 수정 직전에 수정 시각을 현재 시각으로 갱신한다.
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addEvidence(ProjectRequirementEvidence evidence) {
        evidence.setRequirement(this);
        evidences.add(evidence);
    }

    public void replaceEvidences(List<ProjectRequirementEvidence> replacements) {
        evidences.clear();
        replacements.forEach(this::addEvidence);
    }
}
