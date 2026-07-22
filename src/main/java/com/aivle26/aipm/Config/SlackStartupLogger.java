package com.aivle26.aipm.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 Slack/AI 설정이 실제로 주입됐는지 로그로 확인한다.
 *
 * <p>환경변수가 안 들어간 경우 API를 호출해봐야 알 수 있어서 원인 찾기가 번거롭다.
 * 토큰 전체는 찍지 않고 앞 12자만 남긴다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class SlackStartupLogger implements CommandLineRunner {

    private final SlackProperties slackProperties;
    private final AiServerProperties aiServerProperties;

    @Override
    public void run(String... args) {
        log.info("=== 연동 설정 확인 ===");
        if (slackProperties.isConfigured()) {
            String token = slackProperties.getBotToken();
            String masked = token.length() > 12 ? token.substring(0, 12) + "..." : "(짧음)";
            log.info("  SLACK_BOT_TOKEN : 설정됨 [{}] 길이 {}", masked, token.length());
            if (!token.startsWith("xoxb-")) {
                log.warn("  토큰이 xoxb- 로 시작하지 않습니다. Bot 토큰이 맞는지 확인하세요.");
            }
        } else {
            log.warn("  SLACK_BOT_TOKEN : 미설정 → Slack API 호출 시 409 SLACK_NOT_CONNECTED 발생");
            log.warn("  IntelliJ 실행 구성의 '환경 변수' 칸에 SLACK_BOT_TOKEN=xoxb-... 를 넣으세요.");
        }
        log.info("  AI_SERVER_BASE_URL: {}", aiServerProperties.getBaseUrl());
    }
}
