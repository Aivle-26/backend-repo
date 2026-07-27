package com.aivle26.aipm.Dto;

/**
 * 연결 화면에서 고를 수 있는 채널 후보.
 * 채널 ID(C01ABCDEF)를 사람이 직접 입력하게 할 수는 없으므로 목록으로 제공한다.
 */
public record SlackChannelCandidateResponse(
        String channelId,
        String channelName,
        boolean isPrivate,
        /** 봇이 참여하지 않은 채널은 메시지를 읽을 수 없다 */
        boolean botJoined,
        boolean alreadyLinked
) {
}
