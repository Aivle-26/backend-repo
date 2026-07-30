package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AiServerProperties;
import com.aivle26.aipm.Dto.AiMemberDelayRequest;
import com.aivle26.aipm.Dto.AiMemberDelayResponse;
import com.aivle26.aipm.Exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FastAPI AI 서버 호출.
 * 엔드포인트: POST {base-url}/api/v1/risk/member-delay
 */
@Slf4j
@Component
public class HttpMemberDelayAgentClient implements MemberDelayAgentClient {

    private static final String ANALYZE_PATH = "/api/v1/risk/member-delay";

    private final RestClient restClient;

    public HttpMemberDelayAgentClient(AiServerProperties properties) {
        int timeoutMillis = properties.getTimeoutSeconds() * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public AiMemberDelayResponse analyze(AiMemberDelayRequest request) {
        try {
            AiMemberDelayResponse response = restClient.post()
                    .uri(ANALYZE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiMemberDelayResponse.class);

            if (response == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVER_ERROR",
                        "AI 서버가 빈 응답을 반환했습니다.");
            }
            return response;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 서버 호출 실패", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVER_ERROR",
                    "AI 서버 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
