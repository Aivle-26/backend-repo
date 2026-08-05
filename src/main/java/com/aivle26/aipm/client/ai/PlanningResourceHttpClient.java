package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateRequest;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendResponse;
import com.aivle26.aipm.Exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Component
@RequiredArgsConstructor
public class PlanningResourceHttpClient implements PlanningResourceClient {

    static final int MAX_ORGANIZATION_CHART_BYTES = 10 * 1024 * 1024;
    private static final int MAX_BASE64_LENGTH =
            ((MAX_ORGANIZATION_CHART_BYTES + 2) / 3) * 4 + 4;

    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;

    @Override
    public PlanningResourceRecommendResponse recommendAssignments(
            PlanningResourceRecommendRequest request
    ) {
        try {
            PlanningResourceRecommendResponse response = planningAgentRestClient.post()
                    .uri(properties.getResourcePath())
                    .body(request)
                    .retrieve()
                    .body(PlanningResourceRecommendResponse.class);
            if (response == null) {
                throw invalidResponse(null);
            }
            return response;
        } catch (HttpClientErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_RESOURCE_CLIENT_ERROR",
                    "AI Server가 담당자 추천 요청을 처리하지 못했습니다.",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PLANNING_RESOURCE_SERVER_ERROR",
                    "AI Server에서 담당자 추천 중 오류가 발생했습니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PLANNING_RESOURCE_UNAVAILABLE",
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
    public GeneratedOrganizationChart generateOrganizationChart(
            OrganizationChartGenerateRequest request
    ) {
        try {
            OrganizationChartGenerateResponse response = planningAgentRestClient.post()
                    .uri(properties.getOrganizationChartPath())
                    .body(request)
                    .retrieve()
                    .body(OrganizationChartGenerateResponse.class);
            return validateOrganizationChart(request, response);
        } catch (HttpClientErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_AI_CLIENT_ERROR",
                    "The AI Server rejected the organization chart request.",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_AI_SERVER_ERROR",
                    "The AI Server failed to generate the organization chart.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ORGANIZATION_CHART_AI_UNAVAILABLE",
                    "The AI Server is unavailable.",
                    exception
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidOrganizationChartResponse(exception);
        }
    }

    private GeneratedOrganizationChart validateOrganizationChart(
            OrganizationChartGenerateRequest request,
            OrganizationChartGenerateResponse response
    ) {
        if (response == null
                || response.organization() == null
                || request == null
                || request.planningRequest() == null
                || !request.planningRequest().projectId()
                .equals(response.organization().projectId())
                || response.organization().generatedAt() == null
                || response.organization().teams() == null
                || response.organization().roleGaps() == null
                || response.organization().unassignedWbsIds() == null
                || response.fileName() == null
                || response.fileName().isBlank()
                || response.fileName().length() > 255
                || response.fileName().contains("/")
                || response.fileName().contains("\\")
                || !response.fileName().toLowerCase().endsWith(".jpg")
                || !"image/jpeg".equalsIgnoreCase(response.contentType())
                || response.width() <= 0
                || response.height() <= 0
                || response.width() > 7200
                || response.height() > 7200
                || response.imageBase64() == null) {
            throw invalidOrganizationChartResponse(null);
        }

        String encoded = response.imageBase64().trim();
        if (encoded.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "EMPTY_ORGANIZATION_CHART_IMAGE",
                    "The AI Server returned an empty organization chart image."
            );
        }
        if (encoded.length() > MAX_BASE64_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_IMAGE_TOO_LARGE",
                    "The generated organization chart exceeds the size limit."
            );
        }

        byte[] image;
        try {
            image = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_ORGANIZATION_CHART_BASE64",
                    "The AI Server returned invalid organization chart Base64.",
                    exception
            );
        }
        if (image.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "EMPTY_ORGANIZATION_CHART_IMAGE",
                    "The AI Server returned an empty organization chart image."
            );
        }
        if (image.length > MAX_ORGANIZATION_CHART_BYTES) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_IMAGE_TOO_LARGE",
                    "The generated organization chart exceeds the size limit."
            );
        }
        if (image.length < 3
                || (image[0] & 0xff) != 0xff
                || (image[1] & 0xff) != 0xd8
                || (image[2] & 0xff) != 0xff) {
            throw invalidOrganizationChartResponse(null);
        }
        return new GeneratedOrganizationChart(response, image);
    }

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_RESOURCE_RESPONSE",
                "AI Server의 담당자 추천 결과 형식이 올바르지 않습니다.",
                cause
        );
    }

    private ApiException invalidOrganizationChartResponse(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_ORGANIZATION_CHART_RESPONSE",
                "The AI Server organization chart response is invalid.",
                cause
        );
    }
}
