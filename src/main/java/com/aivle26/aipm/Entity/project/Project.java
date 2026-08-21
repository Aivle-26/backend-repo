package com.aivle26.aipm.Entity.project;

import com.aivle26.aipm.Entity.user.User;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(length = 255)
    private String clientOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pm_employee_number", nullable = false)
    private User pm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectStatus status;

    @Column(nullable = false)
    private int progressRate = 0;

    private LocalDate plannedStartDate;

    private LocalDate plannedEndDate;

    @Column(columnDefinition = "TEXT")
    private String acceptanceConditionsJson;

    @Column(columnDefinition = "TEXT")
    private String budgetContractConditionsJson;

    @Column(columnDefinition = "TEXT")
    private String securityPrivacyConditionsJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 프로젝트 최초 저장 시 생성·수정 시각을 현재 시각으로 설정한다.
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.progressRate = Math.max(0, Math.min(100, this.progressRate));
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 프로젝트 수정 직전에 수정 시각을 현재 시각으로 갱신한다.
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
