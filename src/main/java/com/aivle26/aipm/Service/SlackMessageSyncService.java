package com.aivle26.aipm.Service;

import com.aivle26.aipm.Entity.ProjectSlackChannel;
import com.aivle26.aipm.Entity.SlackMessageThread;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectSlackChannelRepository;
import com.aivle26.aipm.Repository.SlackMessageThreadRepository;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Slack 메시지 증분 수집.
 *
 * 채널마다 마지막으로 가져온 ts 이후 메시지만 조회한다.
 * 매번 전체를 다시 받으면 Slack rate limit에 걸리고 비용도 낭비된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackMessageSyncService {

    /** <@U12345> 형태의 멘션 */
    private static final Pattern MENTION_PATTERN = Pattern.compile("<@[UW][A-Z0-9]+>");

    private final SlackClient slackClient;
    private final ProjectSlackChannelRepository projectSlackChannelRepository;
    private final SlackMessageThreadRepository slackMessageThreadRepository;

    /**
     * 프로젝트에 연결된 모든 채널에서 새 메시지를 수집한다.
     *
     * @return 이번에 새로 저장한 메시지 수 (0이면 재분석을 건너뛸 수 있다)
     */
    @Transactional
    public int syncProject(Long projectId) {
        slackClient.requireConfigured();

        List<ProjectSlackChannel> channels = projectSlackChannelRepository.findByProjectId(projectId);
        if (channels.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "SLACK_CHANNEL_NOT_LINKED",
                    "프로젝트에 연결된 Slack 채널이 없습니다. 먼저 채널을 연결하세요.");
        }

        int saved = 0;
        for (ProjectSlackChannel channel : channels) {
            saved += syncChannel(channel);
        }
        log.info("프로젝트 {} Slack 수집 완료: 채널 {}개, 신규 {}건", projectId, channels.size(), saved);
        return saved;
    }

    private int syncChannel(ProjectSlackChannel channel) {
        List<Message> messages = slackClient.fetchHistory(channel.getChannelId(), channel.getLastSyncedTs());

        List<SlackMessageThread> toSave = new ArrayList<>();
        String maxTs = channel.getLastSyncedTs();

        for (Message message : messages) {
            String ts = message.getTs();
            if (ts == null) {
                continue;
            }
            if (maxTs == null || compareTs(ts, maxTs) > 0) {
                maxTs = ts;
            }
            if (!SlackMessageFilter.isAnalyzable(message)) {
                continue;
            }
            // Slack이 oldest 경계값을 포함해 돌려주는 경우가 있어 한 번 더 막는다.
            if (slackMessageThreadRepository.existsByChannelIdAndSlackTs(channel.getChannelId(), ts)) {
                continue;
            }
            toSave.add(toEntity(channel, message));
        }

        if (!toSave.isEmpty()) {
            slackMessageThreadRepository.saveAll(toSave);
        }
        // 새 메시지가 없어도 커서는 갱신한다. 다음 호출에서 같은 구간을 다시 훑지 않도록.
        channel.setLastSyncedTs(maxTs);
        projectSlackChannelRepository.save(channel);

        log.debug("채널 {} 수집: 조회 {}건 / 신규 {}건", channel.getChannelName(), messages.size(), toSave.size());
        return toSave.size();
    }

    private SlackMessageThread toEntity(ProjectSlackChannel channel, Message message) {
        SlackMessageThread entity = new SlackMessageThread();
        entity.setChannelId(channel.getChannelId());
        entity.setChannelName(channel.getChannelName());
        entity.setSlackTs(message.getTs());
        entity.setMessageTs(toDateTime(message.getTs()));
        entity.setThreadTs(message.getThreadTs());
        entity.setUserId(message.getUser());
        entity.setMessageText(message.getText());
        entity.setReplyCount(message.getReplyCount() == null ? 0 : message.getReplyCount());
        entity.setMentionCount(countMentions(message.getText()));
        entity.setReactionSummary(summarizeReactions(message));
        entity.setFileCount(message.getFiles() == null ? 0 : message.getFiles().size());
        return entity;
    }

    /** Slack ts는 "1720000000.123456" 형태의 epoch 초 문자열이다. */
    static LocalDateTime toDateTime(String ts) {
        BigDecimal epoch = new BigDecimal(ts);
        long seconds = epoch.longValue();
        int nanos = epoch.subtract(BigDecimal.valueOf(seconds))
                .multiply(BigDecimal.valueOf(1_000_000_000L))
                .intValue();
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneId.systemDefault());
    }

    /** ts는 소수점 문자열이라 사전순 비교가 틀릴 수 있어 숫자로 비교한다. */
    static int compareTs(String a, String b) {
        return new BigDecimal(a).compareTo(new BigDecimal(b));
    }

    static int countMentions(String text) {
        if (text == null) {
            return 0;
        }
        Matcher matcher = MENTION_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    static String summarizeReactions(Message message) {
        if (message.getReactions() == null || message.getReactions().isEmpty()) {
            return "";
        }
        return message.getReactions().stream()
                .map(r -> r.getName() == null ? "" : r.getName())
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining(","));
    }
}
