package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.ProjectSlackChannel;

import java.time.LocalDateTime;

/** 프로젝트에 연결된 Slack 채널. */
public record SlackChannelResponse(
        Long id,
        String channelId,
        String channelName,
        /** 아직 한 번도 수집하지 않았으면 null */
        String lastSyncedTs,
        LocalDateTime createdAt
) {
    public static SlackChannelResponse from(ProjectSlackChannel entity) {
        return new SlackChannelResponse(
                entity.getId(),
                entity.getChannelId(),
                entity.getChannelName(),
                entity.getLastSyncedTs(),
                entity.getCreatedAt());
    }
}
