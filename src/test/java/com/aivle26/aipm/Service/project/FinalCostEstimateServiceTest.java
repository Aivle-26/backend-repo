package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CostEstimateRequest;
import com.aivle26.aipm.Dto.project.CostEstimateResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectCostEstimate;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalCostEstimateServiceTest {

    @Mock private CostEstimateService costEstimateService;
    @Mock private ProjectAuthorizationService authorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectCostEstimateRepository costEstimateRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FinalCostEstimateService service;

    private Project project;
    private CostEstimateRequest request;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(101L);
        project.setName("AIPM");
        request = new CostEstimateRequest(
                List.of(new CostEstimateRequest.WbsEffort(3L, 0.3)),
                8_000_000L,
                12,
                CostEstimateRequest.ServiceScale.MEDIUM,
                null,
                null,
                null
        );
    }

    @Test
    void savesAuthoritativeCalculatedResultWithResolvedDefaults() {
        stubSaveFinal();
        when(costEstimateRepository.findByProjectId(101L)).thenReturn(Optional.empty());

        var saved = service.saveFinal(101L, request);

        assertThat(saved.costEstimateId()).isEqualTo(77L);
        assertThat(saved.confirmed()).isTrue();
        assertThat(saved.usesAiApi()).isFalse();
        assertThat(saved.paidLicenseUserCount()).isZero();
        assertThat(saved.includeVat()).isTrue();
        assertThat(saved.estimate().totalAmount()).isEqualTo(6_171_000L);
        assertThat(saved.updatedAt()).isNotNull();
    }

    @Test
    void replacesExistingProjectEstimateInsteadOfCreatingAnotherRecord() {
        stubSaveFinal();
        ProjectCostEstimate existing = new ProjectCostEstimate();
        existing.setId(55L);
        existing.setProject(project);
        existing.onCreate();
        when(costEstimateRepository.findByProjectId(101L))
                .thenReturn(Optional.of(existing));

        var saved = service.saveFinal(101L, request);

        verify(costEstimateRepository).saveAndFlush(same(existing));
        assertThat(saved.costEstimateId()).isEqualTo(55L);
        assertThat(existing.getTotalAmount()).isEqualTo(6_171_000L);
        assertThat(existing.isConfirmed()).isTrue();
    }

    @Test
    void getsStoredFinalEstimateWithoutCallingAi() throws Exception {
        ProjectCostEstimate stored = storedFinalEstimate();
        when(costEstimateRepository.findByProjectId(101L)).thenReturn(Optional.of(stored));

        var response = service.getFinal(101L);

        verify(authorizationService).requireProjectPm(101L);
        verifyNoInteractions(costEstimateService);
        assertThat(response.costEstimateId()).isEqualTo(77L);
        assertThat(response.projectId()).isEqualTo(101L);
        assertThat(response.confirmed()).isTrue();
        assertThat(response.wbsEfforts()).containsExactlyElementsOf(request.wbsEfforts());
        assertThat(response.estimate().totalAmount()).isEqualTo(6_171_000L);
        assertThat(response.unpricedItems()).containsExactly("manual review");
    }

    private void stubSaveFinal() {
        when(costEstimateService.estimate(101L, request)).thenReturn(calculated());
        when(projectRepository.findById(101L)).thenReturn(Optional.of(project));
        when(costEstimateRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    ProjectCostEstimate entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(77L);
                        entity.onCreate();
                    } else {
                        entity.onUpdate();
                    }
                    return entity;
                });
    }

    private ProjectCostEstimate storedFinalEstimate() throws Exception {
        CostEstimateResponse result = calculated();
        ProjectCostEstimate entity = new ProjectCostEstimate();
        entity.setId(77L);
        entity.setProject(project);
        entity.setWbsEffortsJson(objectMapper.writeValueAsString(request.wbsEfforts()));
        entity.setAverageMonthlyUnitPrice(request.averageMonthlyUnitPrice());
        entity.setOperationMonths(request.operationMonths());
        entity.setServiceScale(request.serviceScale().name());
        entity.setUsesAiApi(false);
        entity.setPaidLicenseUserCount(0);
        entity.setIncludeVat(true);
        entity.setCurrency(result.currency());
        entity.setTotalEstimatedMm(result.totalEstimatedMm());
        entity.setLaborCost(result.costSummary().laborCost());
        entity.setServerCost(result.costSummary().serverCost());
        entity.setLicenseCost(result.costSummary().licenseCost());
        entity.setAiApiCost(result.costSummary().aiApiCost());
        entity.setBaseCost(result.costSummary().baseCost());
        entity.setContingencyRate(result.estimate().contingencyRate());
        entity.setContingencyAmount(result.estimate().contingencyAmount());
        entity.setSupplyAmount(result.estimate().supplyAmount());
        entity.setVat(result.estimate().vat());
        entity.setTotalAmount(result.estimate().totalAmount());
        entity.setUnpricedItemsJson(objectMapper.writeValueAsString(List.of("manual review")));
        entity.setWarning(result.warning());
        entity.setLlmStatus(result.llmStatus());
        entity.setConfirmed(true);
        entity.onCreate();
        return entity;
    }

    private CostEstimateResponse calculated() {
        return new CostEstimateResponse(
                101L,
                "KRW",
                0.3,
                new CostEstimateResponse.CostSummary(
                        2_400_000L,
                        1_200_000L,
                        600_000L,
                        900_000L,
                        5_100_000L
                ),
                new CostEstimateResponse.Estimate(
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
