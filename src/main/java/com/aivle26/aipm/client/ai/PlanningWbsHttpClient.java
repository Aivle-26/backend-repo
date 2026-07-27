package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Exception.ApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
public class PlanningWbsHttpClient implements PlanningWbsClient {
    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public SaveWbsResultRequest generateWbs(PlanningWbsGenerationRequest request) {
        try {
            byte[] responseBody = planningAgentRestClient.post()
                    .uri(properties.getWbsPath())
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            return decodeResponse(responseBody);
        } catch (HttpClientErrorException exception) {
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

    private SaveWbsResultRequest decodeResponse(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            throw invalidResponse(null);
        }
        try {
            SaveWbsResultRequest response = objectMapper.readValue(responseBody, SaveWbsResultRequest.class);
            if (response == null || response.tasks() == null) {
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

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_WBS_RESPONSE",
                "AI Server의 WBS 결과 형식이 올바르지 않습니다.",
                cause
        );
    }
}
