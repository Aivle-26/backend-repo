package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.WbsGenerationStartResponse;
import com.aivle26.aipm.Dto.project.WbsGenerationStatusResponse;
import com.aivle26.aipm.Entity.project.WbsGenerationStatus;
import com.aivle26.aipm.Service.ProjectArtifactStatusService;
import com.aivle26.aipm.Service.project.AssignmentRecommendationService;
import com.aivle26.aipm.Service.project.CostEstimateService;
import com.aivle26.aipm.Service.project.EditableCostEstimateService;
import com.aivle26.aipm.Service.project.FinalCostEstimateService;
import com.aivle26.aipm.Service.project.KosaEffortEstimateService;
import com.aivle26.aipm.Service.project.ProjectCreationService;
import com.aivle26.aipm.Service.project.ProjectDocumentAnalysisService;
import com.aivle26.aipm.Service.project.ProjectDocumentExtractService;
import com.aivle26.aipm.Service.project.ProjectDocumentService;
import com.aivle26.aipm.Service.project.ProjectLifecycleService;
import com.aivle26.aipm.Service.project.ProjectScheduleService;
import com.aivle26.aipm.Service.project.ProjectService;
import com.aivle26.aipm.Service.project.ProjectWbsGenerationService;
import com.aivle26.aipm.Service.project.ProjectWbsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWbsGenerationControllerTest {
    @Mock private ProjectService projectService;
    @Mock private ProjectLifecycleService projectLifecycleService;
    @Mock private ProjectArtifactStatusService projectArtifactStatusService;
    @Mock private ProjectCreationService projectCreationService;
    @Mock private ProjectDocumentService projectDocumentService;
    @Mock private ProjectDocumentExtractService projectDocumentExtractService;
    @Mock private ProjectDocumentAnalysisService projectDocumentAnalysisService;
    @Mock private ProjectWbsService projectWbsService;
    @Mock private ProjectWbsGenerationService projectWbsGenerationService;
    @Mock private ProjectScheduleService projectScheduleService;
    @Mock private AssignmentRecommendationService assignmentRecommendationService;
    @Mock private CostEstimateService costEstimateService;
    @Mock private FinalCostEstimateService finalCostEstimateService;
    @Mock private EditableCostEstimateService editableCostEstimateService;
    @Mock private KosaEffortEstimateService kosaEffortEstimateService;

    @InjectMocks
    private ProjectController controller;

    @Test
    void returnsAcceptedGenerationIdWithoutWaitingForWbsResult() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 18, 17, 0);
        WbsGenerationStartResponse response = new WbsGenerationStartResponse(
                "generation-1",
                10L,
                WbsGenerationStatus.PROCESSING,
                false,
                requestedAt
        );
        when(projectWbsGenerationService.startGeneration(10L)).thenReturn(response);

        var result = controller.generateWbs(10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void returnsGenerationStatusForPolling() {
        WbsGenerationStatusResponse response = statusResponse("generation-1");
        when(projectWbsGenerationService.getGeneration(10L, "generation-1"))
                .thenReturn(response);

        var result = controller.getWbsGeneration(10L, "generation-1");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void returnsLatestGenerationForPageRestoration() {
        WbsGenerationStatusResponse response = statusResponse("generation-latest");
        when(projectWbsGenerationService.getLatestGeneration(10L)).thenReturn(response);

        var result = controller.getLatestWbsGeneration(10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    private WbsGenerationStatusResponse statusResponse(String generationId) {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 18, 17, 0);
        return new WbsGenerationStatusResponse(
                generationId,
                10L,
                WbsGenerationStatus.SUCCEEDED,
                20L,
                null,
                null,
                requestedAt,
                requestedAt,
                requestedAt.plusSeconds(30)
        );
    }
}
