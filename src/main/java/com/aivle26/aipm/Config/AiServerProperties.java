package com.aivle26.aipm.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 서버(FastAPI) 연동 설정 (app.ai.*). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiServerProperties {

    private String baseUrl = "http://localhost:8000";

    private int timeoutSeconds = 60;

    /**
     * AI 서버에 LLM 사용 여부를 전달한다.
     * false면 규칙 기반으로만 판정하고 llm_status=DISABLED가 돌아온다.
     */
    private boolean enableLlm = true;
}
