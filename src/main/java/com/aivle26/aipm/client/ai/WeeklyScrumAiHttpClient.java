package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyScrumAiHttpClient implements WeeklyScrumAiClient {
    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public AiCallResult summarize(JsonNode request) {
        return invoke(properties.getWeeklyScrumSummarizePath(), request, "summarize");
    }

    @Override
    public AiCallResult review(JsonNode request) {
        return invoke(properties.getWeeklyScrumReviewPath(), request, "review");
    }

    @Override
    public AiCallResult recommendNextActions(JsonNode request) {
        return invoke(properties.getWeeklyScrumRecommendPath(), request, "recommend");
    }

    @Override
    public AiCallResult finalizeReport(JsonNode request) {
        return invoke(properties.getWeeklyScrumFinalizePath(), request, "finalize");
    }

    private AiCallResult invoke(String path, JsonNode request, String stage) {
        String requestId = UUID.randomUUID().toString();
        try {
            ResponseEntity<byte[]> response = planningAgentRestClient.post()
                    .uri(path)
                    .header("X-Request-ID", requestId)
                    .body(request)
                    .retrieve()
                    .toEntity(byte[].class);
            String responseRequestId = response.getHeaders().getFirst("X-Request-ID");
            String traceId = responseRequestId == null || responseRequestId.isBlank()
                    ? requestId
                    : responseRequestId;
            JsonNode body = decode(response.getBody());
            validate(body);
            log.info("Weekly scrum AI stage completed: stage={}, requestId={}, llmStatus={}",
                    stage, traceId, body.path("llm_status").asText());
            return new AiCallResult(body, traceId);
        } catch (HttpStatusCodeException exception) {
            throw mapHttpError(exception, stage, requestId);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "WEEKLY_SCRUM_AI_TIMEOUT",
                        "주간 보고서 AI 처리 시간이 초과되었습니다.",
                        exception
                );
            }
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WEEKLY_SCRUM_AI_UNAVAILABLE",
                    "주간 보고서 AI 서버에 연결할 수 없습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw invalidResponse(exception);
        }
    }

    private JsonNode decode(byte[] body) {
        if (body == null || body.length == 0) {
            throw invalidResponse(null);
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw invalidResponse(exception);
        }
    }

    private void validate(JsonNode body) {
        if (!body.isObject()) {
            throw invalidResponse(null);
        }
        String llmStatus = body.path("llm_status").asText();
        if (!"SUCCEEDED".equals(llmStatus) && !"FALLBACK".equals(llmStatus)) {
            throw invalidResponse(null);
        }
    }

    private ApiException mapHttpError(
            HttpStatusCodeException exception,
            String stage,
            String requestId
    ) {
        int status = exception.getStatusCode().value();
        String upstreamRequestId = responseRequestId(exception.getResponseHeaders(), requestId);
        log.warn("Weekly scrum AI stage failed: stage={}, status={}, requestId={}",
                stage, status, upstreamRequestId);
        return switch (status) {
            case 400, 422 -> new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "WEEKLY_SCRUM_AI_INVALID_REQUEST",
                    "주간 보고서 AI 요청 데이터가 올바르지 않습니다. requestId=" + upstreamRequestId,
                    exception
            );
            case 413 -> new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "WEEKLY_SCRUM_AI_PAYLOAD_TOO_LARGE",
                    "주간 보고서 AI 요청 데이터가 너무 큽니다. requestId=" + upstreamRequestId,
                    exception
            );
            case 503 -> new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WEEKLY_SCRUM_AI_UNAVAILABLE",
                    "주간 보고서 AI 서버를 사용할 수 없습니다. requestId=" + upstreamRequestId,
                    exception
            );
            case 504 -> new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "WEEKLY_SCRUM_AI_TIMEOUT",
                    "주간 보고서 AI 처리 시간이 초과되었습니다. requestId=" + upstreamRequestId,
                    exception
            );
            default -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "WEEKLY_SCRUM_AI_UPSTREAM_ERROR",
                    "주간 보고서 AI 서버 처리 중 오류가 발생했습니다. requestId=" + upstreamRequestId,
                    exception
            );
        };
    }

    private String responseRequestId(HttpHeaders headers, String fallback) {
        if (headers == null) {
            return fallback;
        }
        String value = headers.getFirst("X-Request-ID");
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_WEEKLY_SCRUM_AI_RESPONSE",
                "주간 보고서 AI 응답 형식이 올바르지 않습니다.",
                cause
        );
    }
}
