package com.aivle26.aipm.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 프로젝트에 Slack 채널을 연결할 때 보내는 요청. */
public record RegisterSlackChannelRequest(
        @NotBlank(message = "channelId는 필수입니다.")
        @Size(max = 50)
        String channelId
) {
}
