package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateRequest;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateResponse;
import com.aivle26.aipm.Dto.project.KosaEffortEstimate;
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
public class PlanningCostHttpClient implements PlanningCostClient {

    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;

    @Override
    public PlanningCostEstimateResponse estimate(PlanningCostEstimateRequest request) {
        try {
            PlanningCostEstimateResponse response = planningAgentRestClient.post()
                    .uri(properties.getCostPath())
                    .body(request)
                    .retrieve()
                    .body(PlanningCostEstimateResponse.class);
            if (response == null) {
                throw invalidResponse(null);
            }
            return response;
        } catch (HttpClientErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_COST_CLIENT_ERROR",
                    "AI Server가 예상 견적 요청을 처리하지 못했습니다.",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_COST_SERVER_ERROR",
                    "AI Server에서 예상 견적 생성 중 오류가 발생했습니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PLANNING_COST_UNAVAILABLE",
                    "AI Server에 연결할 수 없습니다.",
                    exception
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidResponse(exception);
        }
    }

    @Override
    public KosaEffortEstimate.AiResponse estimateEffort(
            KosaEffortEstimate.AiRequest request,
            String requestId
    ) {
        try {
            KosaEffortEstimate.AiResponse response = planningAgentRestClient.post()
                    .uri(properties.getEffortEstimatePath())
                    .header("X-Request-ID", requestId)
                    .body(request)
                    .retrieve()
                    .toEntity(KosaEffortEstimate.AiResponse.class)
                    .getBody();
            if (response == null) {
                throw invalidEffortResponse(null);
            }
            return response;
        } catch (HttpClientErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_EFFORT_CLIENT_ERROR",
                    "AI Server가 공수 산정 요청을 처리하지 못했습니다.",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            HttpStatus status = exception.getStatusCode().value() == 503
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            throw new ApiException(
                    status,
                    "PLANNING_EFFORT_SERVER_ERROR",
                    "AI Server에서 공수 산정 중 오류가 발생했습니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PLANNING_EFFORT_UNAVAILABLE",
                    "AI Server에 연결할 수 없습니다.",
                    exception
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidEffortResponse(exception);
        }
    }

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_COST_RESPONSE",
                "AI Server의 예상 견적 결과 형식이 올바르지 않습니다.",
                cause
        );
    }

    private ApiException invalidEffortResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_EFFORT_RESPONSE",
                "AI Server의 공수 산정 결과 형식이 올바르지 않습니다.",
                cause
        );
    }
}
