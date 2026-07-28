package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AiServerProperties;
import com.aivle26.aipm.Dto.AiSecurityCheckRequest;
import com.aivle26.aipm.Dto.AiSecurityCheckResponse;
import com.aivle26.aipm.Exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FastAPI AI 서버 호출.
 * 엔드포인트: POST {base-url}/api/v1/risk/artifact-security
 */
@Slf4j
@Component
public class HttpSecurityCheckAgentClient implements SecurityCheckAgentClient {

    private static final String INSPECT_PATH = "/api/v1/risk/artifact-security";

    private final RestClient restClient;

    public HttpSecurityCheckAgentClient(AiServerProperties properties) {
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
    public AiSecurityCheckResponse inspect(AiSecurityCheckRequest request) {
        try {
            AiSecurityCheckResponse response = restClient.post()
                    .uri(INSPECT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiSecurityCheckResponse.class);

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
