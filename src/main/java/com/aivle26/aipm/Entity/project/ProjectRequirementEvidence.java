package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
        name = "project_requirement_evidences",
        indexes = {
                @Index(
                        name = "idx_requirement_evidence_requirement",
                        columnList = "requirement_id"
                ),
                @Index(
                        name = "idx_requirement_evidence_document",
                        columnList = "document_id"
                )
        }
)
public class ProjectRequirementEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private ProjectRequirement requirement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private ProjectDocument document;

    private Integer pageNumber;

    @Column(nullable = false, length = 255)
    private String chunkId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String quoteText;

    private Integer startOffset;

    private Integer endOffset;

    @Column(columnDefinition = "TEXT")
    private String boundingBoxesJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
