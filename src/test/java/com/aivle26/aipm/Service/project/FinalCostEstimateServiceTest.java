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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalCostEstimateServiceTest {

    @Mock private CostEstimateService costEstimateService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectCostEstimateRepository costEstimateRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private FinalCostEstimateService service;

    private Project project;
    private CostEstimateRequest request;

    @BeforeEach
    void setUp() throws Exception {
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

        when(costEstimateService.estimate(101L, request)).thenReturn(calculated());
        when(projectRepository.findById(101L)).thenReturn(Optional.of(project));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
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

    @Test
    void savesAuthoritativeCalculatedResultWithResolvedDefaults() {
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
