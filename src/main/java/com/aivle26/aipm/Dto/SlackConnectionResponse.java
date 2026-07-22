package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * Slack 연결 상태 점검 결과.
 * 토큰이 유효한지, 봇이 어느 채널에 들어가 있는지를 한 번에 확인한다.
 */
public record SlackConnectionResponse(
        boolean connected,
        int totalChannels,
        /** 봇이 참여한 채널. 여기 없는 채널은 메시지를 읽을 수 없다. */
        List<Channel> joinedChannels,
        /** 봇이 아직 초대되지 않은 채널 */
        List<Channel> notJoinedChannels
) {
    public record Channel(String channelId, String channelName, boolean isPrivate) {
    }
}
