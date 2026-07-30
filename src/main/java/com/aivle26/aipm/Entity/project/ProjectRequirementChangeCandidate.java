package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "project_requirement_change_candidates",
        indexes = {
                @Index(
                        name = "idx_requirement_change_project",
                        columnList = "project_id"
                ),
                @Index(
                        name = "idx_requirement_change_existing",
                        columnList = "existing_requirement_id"
                )
        }
)
public class ProjectRequirementChangeCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "existing_requirement_id")
    private ProjectRequirement existingRequirement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementChangeReviewStatus reviewStatus;

    @Column(nullable = false, length = 1000)
    private String changeReason;

    @Column(columnDefinition = "TEXT")
    private String existingRequirementJson;

    @Column(columnDefinition = "TEXT")
    private String proposedRequirementJson;

    @Column(columnDefinition = "TEXT")
    private String evidencesJson;

    private LocalDateTime baseRequirementUpdatedAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime appliedAt;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.reviewStatus == null) {
            this.reviewStatus = RequirementChangeReviewStatus.PENDING_REVIEW;
        }
    }
}
