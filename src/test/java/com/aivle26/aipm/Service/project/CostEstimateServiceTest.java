package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CostEstimateRequest;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateRequest;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.PlanningCostClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostEstimateServiceTest {

    @Mock private ProjectAuthorizationService projectAuthorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectWbsTaskRepository wbsTaskRepository;
    @Mock private PlanningCostClient planningCostClient;

    @InjectMocks
    private CostEstimateService service;

    private ProjectWbsTask task;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setId(101L);
        project.setName("AIPM");

        task = new ProjectWbsTask();
        task.setId(3L);
        task.setTaskName("API implementation");
        task.setDescription("Integrate the AI API");
        task.setOrderIndex(1);
        task.setConfirmed(true);

        when(projectRepository.findById(101L)).thenReturn(Optional.of(project));
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(101L))
                .thenReturn(List.of(task));
    }

    @Test
    void enrichesWbsAndAppliesOptionalDefaultsBeforeCallingAi() {
        when(planningCostClient.estimate(any())).thenReturn(aiResponse());

        var response = service.estimate(101L, request(List.of(
                new CostEstimateRequest.WbsEffort(3L, 0.3)
        )));

        ArgumentCaptor<PlanningCostEstimateRequest> captor =
                ArgumentCaptor.forClass(PlanningCostEstimateRequest.class);
        verify(planningCostClient).estimate(captor.capture());
        PlanningCostEstimateRequest sent = captor.getValue();

        assertThat(sent.projectName()).isEqualTo("AIPM");
        assertThat(sent.wbsEfforts().getFirst().wbsName())
                .isEqualTo("API implementation");
        assertThat(sent.wbsEfforts().getFirst().description())
                .isEqualTo("Integrate the AI API");
        assertThat(sent.usesAiApi()).isFalse();
        assertThat(sent.paidLicenseUserCount()).isZero();
        assertThat(sent.includeVat()).isTrue();
        assertThat(response.costSummary().laborCost()).isEqualTo(2_400_000L);
        assertThat(response.estimate().totalAmount()).isEqualTo(6_171_000L);
        assertThat(response.llmStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void rejectsRequestThatDoesNotCoverEveryBillableWbs() {
        ProjectWbsTask second = new ProjectWbsTask();
        second.setId(4L);
        second.setTaskName("Frontend implementation");
        second.setDescription("Build UI");
        second.setOrderIndex(2);
        second.setConfirmed(true);
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(101L))
                .thenReturn(List.of(task, second));

        assertThatThrownBy(() -> service.estimate(101L, request(List.of(
                new CostEstimateRequest.WbsEffort(3L, 0.3)
        ))))
                .isInstanceOf(ApiException.class)
                .hasMessage("wbs efforts must contain every billable wbs task exactly once");
        verify(planningCostClient, never()).estimate(any());
    }

    private CostEstimateRequest request(List<CostEstimateRequest.WbsEffort> efforts) {
        return new CostEstimateRequest(
                efforts,
                8_000_000L,
                12,
                CostEstimateRequest.ServiceScale.MEDIUM,
                null,
                null,
                null
        );
    }

    private PlanningCostEstimateResponse aiResponse() {
        return new PlanningCostEstimateResponse(
                101L,
                "KRW",
                0.3,
                new PlanningCostEstimateResponse.CostSummary(
                        2_400_000L,
                        1_200_000L,
                        600_000L,
                        900_000L,
                        5_100_000L
                ),
                new PlanningCostEstimateResponse.Estimate(
                        10,
                        510_000L,
                        5_610_000L,
                        561_000L,
                        6_171_000L
                ),
                List.of(),
                "Actual costs may vary.",
                "SUCCEEDED"
        );
    }
}
