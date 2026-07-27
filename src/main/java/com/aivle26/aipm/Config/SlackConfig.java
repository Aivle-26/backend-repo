package com.aivle26.aipm.Config;

import com.slack.api.Slack;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Slack SDK 진입점 등록.
 * {@link Slack}은 thread-safe하고 내부 HTTP 클라이언트를 재사용하므로 하나를 공유한다.
 */
@Configuration
@EnableConfigurationProperties({SlackProperties.class, AiServerProperties.class})
public class SlackConfig {

    @Bean
    public Slack slack() {
        return Slack.getInstance();
    }
}
