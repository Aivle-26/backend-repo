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
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "project_cost_estimates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_cost_estimate_project",
                columnNames = "project_id"
        )
)
public class ProjectCostEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String wbsEffortsJson;

    @Column(nullable = false)
    private long averageMonthlyUnitPrice;

    @Column(nullable = false)
    private int operationMonths;

    @Column(nullable = false, length = 20)
    private String serviceScale;

    @Column(nullable = false)
    private boolean usesAiApi;

    @Column(nullable = false)
    private int paidLicenseUserCount;

    @Column(nullable = false)
    private boolean includeVat;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private double totalEstimatedMm;

    @Column(nullable = false)
    private long laborCost;

    @Column(nullable = false)
    private long serverCost;

    @Column(nullable = false)
    private long licenseCost;

    @Column(nullable = false)
    private long aiApiCost;

    @Column(nullable = false)
    private long baseCost;

    @Column(nullable = false)
    private int contingencyRate;

    @Column(nullable = false)
    private long contingencyAmount;

    @Column(nullable = false)
    private long supplyAmount;

    @Column(nullable = false)
    private long vat;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String unpricedItemsJson;

    @Column(nullable = false, length = 2000)
    private String warning;

    @Column(nullable = false, length = 30)
    private String llmStatus;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
