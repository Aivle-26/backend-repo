package com.aivle26.aipm.Entity;

import com.aivle26.aipm.Entity.project.Project;
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

/**
 * 프로젝트 ↔ Slack 채널 매핑.
 *
 * <p>프로젝트 하나가 #project-backend, #project-frontend 처럼 여러 채널을 쓰는 경우가
 * 흔해서 Project에 컬럼을 붙이지 않고 별도 매핑 테이블로 둔다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "project_slack_channels",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_slack_channel",
                columnNames = {"project_id", "channel_id"}
        )
)
public class ProjectSlackChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Slack 채널 ID (C로 시작) */
    @Column(name = "channel_id", nullable = false, length = 50)
    private String channelId;

    @Column(nullable = false, length = 200)
    private String channelName;

    /**
     * 마지막으로 수집한 메시지의 Slack ts (예: "1720000000.123456").
     *
     * <p>MAX(message_ts) 집계로 대신하지 않고 컬럼으로 둔다. 집계로 구하면
     * 메시지가 0건인 채널에서 "아직 안 가져옴"과 "가져왔는데 비어있음"을 구분할 수 없다.
     */
    @Column(length = 50)
    private String lastSyncedTs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
