package com.aivle26.aipm.Service;

import com.slack.api.model.Message;

import java.util.Set;

/**
 * 분석 대상 메시지 판별.
 *
 * <p>Slack은 "OOO님이 채널에 참여함" 같은 시스템 알림도 메시지로 내려준다.
 * 이런 메시지엔 &lt;@U...&gt; 형태가 들어있어 mention_count가 1로 잡히는데,
 * AI 서버는 "멘션 있음 + 답글 0 + 24시간 경과"를 장기 미응답으로 판정하므로
 * 걸러내지 않으면 참여 알림이 전부 리스크로 둔갑해 위험도가 부풀려진다.
 */
final class SlackMessageFilter {

    /**
     * 제외할 시스템 subtype.
     *
     * <p>subtype이 있다고 무조건 빼지는 않는다. file_share나 thread_broadcast는
     * 사람이 보낸 진짜 메시지라 남겨야 한다.
     */
    private static final Set<String> SYSTEM_SUBTYPES = Set.of(
            "channel_join",
            "channel_leave",
            "channel_topic",
            "channel_purpose",
            "channel_name",
            "channel_archive",
            "channel_unarchive",
            "group_join",
            "group_leave",
            "bot_message",
            "pinned_item",
            "unpinned_item"
    );

    private SlackMessageFilter() {
    }

    static boolean isAnalyzable(Message message) {
        if (message == null || message.getTs() == null) {
            return false;
        }
        if (message.getText() == null || message.getText().isBlank()) {
            return false;
        }
        // 봇 메시지는 user가 비어 있다
        if (message.getUser() == null || message.getUser().isBlank()) {
            return false;
        }
        String subtype = message.getSubtype();
        return subtype == null || !SYSTEM_SUBTYPES.contains(subtype);
    }
}
