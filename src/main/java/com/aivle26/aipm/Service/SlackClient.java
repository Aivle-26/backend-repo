package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.SlackProperties;
import com.aivle26.aipm.Exception.ApiException;
import com.slack.api.Slack;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Slack Web API 호출을 한 곳에 모은 얇은 래퍼.
 *
 * <p>SDK 호출과 오류 변환을 여기서만 하므로, Slack 쪽 스펙이 바뀌어도
 * 고칠 지점이 한 파일로 제한된다.
 *
 * <p>DM(im)·그룹DM(mpim)은 조회하지 않는다. 프로젝트 채널 분석에 개인 대화는
 * 필요 없고, 읽으면 팀원 동의 문제가 생긴다.
 */
@Component
@RequiredArgsConstructor
public class SlackClient {

    private final Slack slack;
    private final SlackProperties slackProperties;

    /** 봇이 볼 수 있는 공개·비공개 채널 목록 */
    public List<Conversation> listChannels() {
        requireConfigured();
        try {
            ConversationsListResponse res = slack.methods(slackProperties.getBotToken())
                    .conversationsList(r -> r
                            .types(List.of(ConversationType.PUBLIC_CHANNEL, ConversationType.PRIVATE_CHANNEL))
                            .excludeArchived(true)
                            .limit(200));
            requireOk(res.isOk(), "conversations.list", res.getError());
            return res.getChannels() == null ? List.of() : res.getChannels();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SLACK_API_ERROR",
                    "Slack 채널 목록 조회 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 채널 메시지 조회.
     *
     * @param oldest 이 ts 이후 메시지만. null이면 최근 것부터 fetchLimit 만큼.
     */
    public List<Message> fetchHistory(String channelId, String oldest) {
        requireConfigured();
        try {
            ConversationsHistoryResponse res = slack.methods(slackProperties.getBotToken())
                    .conversationsHistory(r -> {
                        r.channel(channelId).limit(slackProperties.getFetchLimit());
                        if (oldest != null) {
                            r.oldest(oldest);
                        }
                        return r;
                    });
            requireOk(res.isOk(), "conversations.history", res.getError());
            return res.getMessages() == null ? List.of() : res.getMessages();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SLACK_API_ERROR",
                    "Slack 메시지 조회 중 오류가 발생했습니다.", e);
        }
    }

    public void requireConfigured() {
        if (!slackProperties.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT, "SLACK_NOT_CONNECTED",
                    "Slack Bot 토큰이 설정되지 않았습니다. SLACK_BOT_TOKEN을 확인하세요.");
        }
    }

    private void requireOk(boolean ok, String method, String error) {
        if (ok) {
            return;
        }
        // not_in_channel: 봇이 채널에 초대되지 않음 (사람이 Slack에서 초대해야 함)
        // invalid_auth / account_inactive: 토큰이 잘못됐거나 앱이 비활성
        // missing_scope: OAuth Scope 부족
        throw new ApiException(HttpStatus.BAD_GATEWAY, "SLACK_API_ERROR",
                "Slack " + method + " 실패: " + error);
    }
}
