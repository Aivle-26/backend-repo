package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationResponse;
import com.aivle26.aipm.Exception.ApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningWbsHttpClient implements PlanningWbsClient {
    private static final int MAX_LOG_DETAIL_LENGTH = 2_000;
    private static final int MAX_VALIDATION_ERROR_COUNT = 20;

    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PlanningWbsGenerationResponse generateWbs(PlanningWbsGenerationRequest request) {
        try {
            byte[] responseBody = planningAgentRestClient.post()
                    .uri(properties.getWbsPath())
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            return decodeResponse(responseBody);
        } catch (HttpClientErrorException exception) {
            log.warn(
                    "Planning WBS AI request rejected: status={}, method=POST, path={}, detail={}",
                    exception.getStatusCode().value(),
                    properties.getWbsPath(),
                    safeErrorDetail(exception.getResponseBodyAsByteArray())
            );
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_WBS_CLIENT_ERROR",
                    "AI Server가 WBS 생성 요청을 처리하지 못했습니다.",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_WBS_SERVER_ERROR",
                    "AI Server에서 WBS 생성 중 오류가 발생했습니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "PLANNING_WBS_TIMEOUT",
                        "WBS 생성 처리 시간이 초과되었습니다.",
                        exception
                );
            }
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PLANNING_WBS_UNAVAILABLE",
                    "AI Server에 연결할 수 없습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw invalidResponse(exception);
        }
    }

    private PlanningWbsGenerationResponse decodeResponse(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            throw invalidResponse(null);
        }
        try {
            PlanningWbsGenerationResponse response =
                    objectMapper.readValue(responseBody, PlanningWbsGenerationResponse.class);
            if (response == null || response.wbsItems() == null || response.wbsItems().isEmpty()) {
                throw invalidResponse(null);
            }
            return response;
        } catch (IOException exception) {
            throw invalidResponse(exception);
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // FastAPI validation responses can echo request input, so only whitelisted error fields are logged.
    private String safeErrorDetail(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return "<empty response>";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode detail = root.get("detail");
            if (detail != null && !detail.isNull()) {
                if (detail.isTextual()) {
                    return sanitizeAndTruncate(detail.asText());
                }
                if (detail.isArray()) {
                    return safeValidationErrors(detail);
                }
                return safeObjectFields(detail);
            }
            return safeObjectFields(root);
        } catch (IOException exception) {
            return "<non-json response omitted; bytes=" + responseBody.length + ">";
        }
    }

    private String safeValidationErrors(JsonNode errors) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (JsonNode error : errors) {
            if (count >= MAX_VALIDATION_ERROR_COUNT) {
                result.append("; ... additional validation errors omitted");
                break;
            }
            if (count > 0) {
                result.append("; ");
            }
            result.append("type=").append(safeTextField(error, "type"));
            result.append(", loc=").append(safeLocation(error.get("loc")));
            result.append(", msg=").append(safeTextField(error, "msg"));
            count++;
        }
        return truncate(result.length() == 0 ? "<empty validation detail>" : result.toString());
    }

    private String safeObjectFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "<structured error detail omitted>";
        }
        StringBuilder result = new StringBuilder();
        appendSafeField(result, node, "code");
        appendSafeField(result, node, "error");
        appendSafeField(result, node, "message");
        appendSafeField(result, node, "type");
        return truncate(result.length() == 0 ? "<structured error detail omitted>" : result.toString());
    }

    private void appendSafeField(StringBuilder result, JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isValueNode()) {
            return;
        }
        if (result.length() > 0) {
            result.append(", ");
        }
        result.append(fieldName).append("=").append(sanitizeAndTruncate(value.asText()));
    }

    private String safeTextField(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isValueNode()
                ? sanitizeAndTruncate(value.asText())
                : "<unknown>";
    }

    private String safeLocation(JsonNode location) {
        if (location == null || !location.isArray()) {
            return "<unknown>";
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode part : location) {
            if (part.isValueNode()) {
                result.append('/').append(sanitizeAndTruncate(part.asText()));
            }
        }
        return result.length() == 0 ? "<unknown>" : truncate(result.toString());
    }

    private String sanitizeAndTruncate(String value) {
        if (value == null) {
            return "<unknown>";
        }
        return truncate(value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim());
    }

    private String truncate(String value) {
        if (value.length() <= MAX_LOG_DETAIL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_DETAIL_LENGTH) + "...<truncated>";
    }

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_WBS_RESPONSE",
                "AI Server의 WBS 결과 형식이 올바르지 않습니다.",
                cause
        );
    }
}
