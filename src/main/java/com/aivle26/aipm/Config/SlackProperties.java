package com.aivle26.aipm.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Slack 연동 설정 (app.slack.*).
 *
 * <p>워크스페이스가 하나인 현재 단계에서는 OAuth 설치 흐름 없이 환경변수로 주입한
 * Bot 토큰 하나만 쓴다. Bot 토큰을 쓰는 이유는 User 토큰이 발급한 개인에 묶여 있어
 * 그 사람이 나가거나 앱 권한을 해제하면 분석이 통째로 멈추기 때문이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.slack")
public class SlackProperties {

    /** xoxb- 로 시작하는 Bot 토큰 */
    private String botToken = "";

    /** conversations.history 1회 호출당 최대 메시지 수 */
    private int fetchLimit = 200;

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }
}
