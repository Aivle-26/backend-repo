package com.aivle26.aipm.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** AI 서버(FastAPI)의 CommunicationRiskResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCommunicationRiskResponse(
        @JsonProperty("project_id") Long projectId,
        @JsonProperty("project_name") String projectName,
        @JsonProperty("communication_risk_level") String communicationRiskLevel,
        @JsonProperty("reasons") List<String> reasons,
        @JsonProperty("evidence_messages") List<AiEvidenceMessage> evidenceMessages,
        @JsonProperty("recommended_action") String recommendedAction,
        @JsonProperty("metrics") AiMetrics metrics,
        @JsonProperty("analysis_window") Map<String, LocalDateTime> analysisWindow,
        @JsonProperty("llm_status") String llmStatus
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiEvidenceMessage(
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("channel_name") String channelName,
            @JsonProperty("message_ts") LocalDateTime messageTs,
            @JsonProperty("thread_ts") String threadTs,
            @JsonProperty("message_text") String messageText
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiMetrics(
            @JsonProperty("recent_7d_message_count") int recent7dMessageCount,
            @JsonProperty("previous_7d_message_count") int previous7dMessageCount,
            /** 이전 7일이 0건이면 AI 서버가 null을 준다 */
            @JsonProperty("activity_change_percent") Double activityChangePercent,
            @JsonProperty("long_unanswered_count") int longUnansweredCount
    ) {
    }
}
