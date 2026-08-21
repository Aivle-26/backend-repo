package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationResponse;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.client.ai.PlanningWbsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectWbsGenerationWorker {
    private final ProjectWbsService projectWbsService;
    private final PlanningWbsClient planningWbsClient;
    private final ProjectWbsGenerationService generationService;

    public void run(String generationId, Long projectId) {
        try {
            PlanningWbsGenerationRequest request =
                    projectWbsService.prepareGenerationRequest(projectId);
            PlanningWbsGenerationResponse response = planningWbsClient.generateWbs(request);
            Long wbsResultId = projectWbsService.saveGeneratedWbs(projectId, response);
            generationService.markSucceeded(generationId, wbsResultId);
        } catch (Exception exception) {
            log.error(
                    "WBS generation failed. generationId={}, projectId={}",
                    generationId,
                    projectId,
                    exception
            );
            if (exception instanceof ApiException apiException) {
                generationService.markFailed(
                        generationId,
                        apiException.getCode() == null
                                ? "WBS_GENERATION_FAILED"
                                : apiException.getCode(),
                        apiException.getMessage()
                );
                return;
            }
            generationService.markFailed(
                    generationId,
                    "WBS_GENERATION_FAILED",
                    "WBS 생성 중 오류가 발생했습니다."
            );
        }
    }
}
