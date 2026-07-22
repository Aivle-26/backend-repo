package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.SlackMessageThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SlackMessageThreadRepository extends JpaRepository<SlackMessageThread, Long> {

    boolean existsByChannelIdAndSlackTs(String channelId, String slackTs);

    /** 분석 대상 메시지. AI 서버가 최근 7일과 이전 7일을 비교하므로 14일치를 넘긴다. */
    List<SlackMessageThread> findByChannelIdInAndMessageTsBetweenOrderByMessageTsAsc(
            List<String> channelIds, LocalDateTime from, LocalDateTime to);

    long countByChannelId(String channelId);
}
