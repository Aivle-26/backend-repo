package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 서버(FastAPI)의 CommunicationRiskRequest.
 * 필드명이 snake_case라 @JsonProperty로 명시한다.
 *
 * <p>messages는 1건 이상이어야 한다. 빈 배열로 부르면 422가 돌아온다.
 */
public record AiCommunicationRiskRequest(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("project_name") String projectName,
        @JsonProperty("analysis_end") LocalDateTime analysisEnd,
        @JsonProperty("enable_llm") boolean enableLlm,
        @JsonProperty("messages") List<AiSlackMessage> messages
) {
    /** AI 서버의 SlackMessageThreadInput */
    public record AiSlackMessage(
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("channel_name") String channelName,
            @JsonProperty("message_ts") LocalDateTime messageTs,
            @JsonProperty("thread_ts") String threadTs,
            @JsonProperty("user_id") String userId,
            @JsonProperty("message_text") String messageText,
            @JsonProperty("reply_count") int replyCount,
            @JsonProperty("mention_count") int mentionCount,
            @JsonProperty("reaction_summary") String reactionSummary,
            @JsonProperty("file_count") int fileCount
    ) {
    }
}
