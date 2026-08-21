package com.aivle26.aipm.Entity;

import com.aivle26.aipm.Entity.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 서버가 판정한 커뮤니케이션 리스크 결과.
 *
 * <p>지표는 나중에 추이 조회가 가능하도록 컬럼으로 펼치고,
 * 개수가 가변인 reasons/evidenceMessages만 JSON 문자열로 저장한다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "communication_risk_results")
public class CommunicationRiskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationRiskLevel riskLevel;

    /** 판정 근거 문자열 배열의 JSON (AI 서버가 1~3건 반환) */
    @Lob
    @Column(nullable = false)
    private String reasonsJson;

    /** 근거 메시지 배열의 JSON (AI 서버가 최대 3건 반환) */
    @Lob
    @Column(nullable = false)
    private String evidenceMessagesJson;

    @Column(nullable = false, length = 1000)
    private String recommendedAction;

    @Column(nullable = false)
    private int recent7dMessageCount;

    @Column(nullable = false)
    private int previous7dMessageCount;

    /** 이전 7일 메시지가 0건이면 계산 불가라 null이 들어간다. */
    private Double activityChangePercent;

    @Column(nullable = false)
    private int longUnansweredCount;

    @Column(nullable = false)
    private LocalDateTime analysisWindowStart;

    @Column(nullable = false)
    private LocalDateTime analysisWindowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationLlmStatus llmStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    public void onCreate() {
        if (this.analyzedAt == null) {
            this.analyzedAt = LocalDateTime.now();
        }
    }
}
