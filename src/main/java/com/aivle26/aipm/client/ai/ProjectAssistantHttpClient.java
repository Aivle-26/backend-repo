package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.DeliverableRagRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryResponse;
import com.aivle26.aipm.Exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ProjectAssistantHttpClient implements ProjectAssistantClient {

    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;

    @Override
    public ProjectAssistantQueryResponse query(DeliverableRagRequest request) {
        try {
            ProjectAssistantQueryResponse response = planningAgentRestClient.post()
                    .uri(properties.getRagQueryPath())
                    .body(request)
                    .retrieve()
                    .body(ProjectAssistantQueryResponse.class);
            if (response == null) {
                throw invalidResponse(null);
            }
            return response;
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROJECT_ASSISTANT_SERVER_ERROR",
                    "AI 서버가 프로젝트 챗봇 요청을 처리하지 못했습니다.", exception);
        } catch (ResourceAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROJECT_ASSISTANT_UNAVAILABLE",
                    "AI 서버에 연결할 수 없습니다.", exception);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidResponse(exception);
        }
    }

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PROJECT_ASSISTANT_RESPONSE",
                "AI 서버의 프로젝트 챗봇 응답 형식이 올바르지 않습니다.", cause);
    }
}
