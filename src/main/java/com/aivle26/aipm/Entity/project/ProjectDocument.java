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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "project_documents")
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectDocumentStatus status;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false, length = 50)
    private String extension;

    @Column(length = 255)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    private Long characterCount;

    @Column(length = 50)
    private String fileType;

    @Column(length = 50)
    private String processingMode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 문서 메타데이터 최초 저장 시 생성 시각을 현재 시각으로 설정한다.
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
