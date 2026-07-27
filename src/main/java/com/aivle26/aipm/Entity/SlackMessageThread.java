package com.aivle26.aipm.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 수집한 Slack 메시지.
 *
 * <p>컬럼명은 AI 서버 입력 스키마(SlackMessageThreadInput)와 1:1로 맞췄다.
 * 변환 코드를 줄이고 스키마가 어긋나면 바로 눈에 띄게 하기 위함이다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "slack_message_threads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_slack_message",
                columnNames = {"channel_id", "slack_ts"}
        ),
        indexes = @Index(name = "idx_slack_message_channel_time", columnList = "channel_id, message_ts")
)
public class SlackMessageThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false, length = 50)
    private String channelId;

    @Column(nullable = false, length = 200)
    private String channelName;

    /**
     * Slack 원본 ts 문자열. 메시지의 진짜 고유 키라 중복 방지 UK에 쓴다.
     *
     * <p>Slack은 oldest 경계값을 포함해 돌려주는 경우가 있어 같은 메시지가 두 번 들어올 수 있다.
     * 그대로 두면 대화량 지표가 부풀려져 위험도가 조용히 잘못 나온다.
     */
    @Column(name = "slack_ts", nullable = false, length = 50)
    private String slackTs;

    /** slackTs를 시각으로 변환한 값. AI 서버에 ISO 8601로 넘긴다. */
    @Column(name = "message_ts", nullable = false)
    private LocalDateTime messageTs;

    @Column(length = 50)
    private String threadTs;

    @Column(nullable = false, length = 50)
    private String userId;

    @Lob
    @Column(nullable = false)
    private String messageText;

    @Column(nullable = false)
    private int replyCount;

    @Column(nullable = false)
    private int mentionCount;

    /** 리액션 이름을 쉼표로 이은 요약 (예: "white_check_mark,eyes") */
    @Column(length = 500)
    private String reactionSummary;

    @Column(nullable = false)
    private int fileCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
