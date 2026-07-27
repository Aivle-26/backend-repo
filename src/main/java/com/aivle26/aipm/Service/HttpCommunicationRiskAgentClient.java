package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AiServerProperties;
import com.aivle26.aipm.Dto.AiCommunicationRiskRequest;
import com.aivle26.aipm.Dto.AiCommunicationRiskResponse;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FastAPI AI 서버 호출.
 * 엔드포인트: POST {base-url}/api/v1/risk/communication/analyze
 */
@Slf4j
@Component
public class HttpCommunicationRiskAgentClient implements CommunicationRiskAgentClient {

    private static final String ANALYZE_PATH = "/api/v1/risk/communication/analyze";

    private final RestClient restClient;

    public HttpCommunicationRiskAgentClient(AiServerProperties properties, ObjectMapper objectMapper) {
        int timeoutMillis = properties.getTimeoutSeconds() * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                // RestClient.builder()는 기본 ObjectMapper로 컨버터를 만드는데,
                // 그 기본값은 LocalDateTime을 [2026,7,21,17,56,14] 같은 숫자 배열로 직렬화한다.
                // FastAPI(pydantic)는 ISO 8601 문자열을 기대하므로 422가 난다.
                // Spring이 설정해둔 ObjectMapper(JavaTimeModule + ISO 포맷)를 쓰도록 교체한다.
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }

    @Override
    public AiCommunicationRiskResponse analyze(AiCommunicationRiskRequest request) {
        try {
            AiCommunicationRiskResponse response = restClient.post()
                    .uri(ANALYZE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiCommunicationRiskResponse.class);

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
