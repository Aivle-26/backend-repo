package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.EditableCostEstimate;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditableCostEstimateServiceTest {

    @Mock private ProjectAuthorizationService authorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTaskAssignmentRepository assignmentRepository;
    @Mock private ProjectCostEstimateRepository costEstimateRepository;
    @Mock private UserRepository userRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private EditableCostEstimateService service;

    @Test
    void recalculatesEveryDisplayedAmountOnServer() {
        Project project = new Project();
        project.setId(1L);
        ProjectTaskAssignment assignment = new ProjectTaskAssignment();
        assignment.setEmployeeNumber("E1");
        User user = new User();
        user.setEmployeeNumber("E1");
        user.setName("홍길동");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(1L))
                .thenReturn(List.of(assignment));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        EditableCostEstimate.Request request = new EditableCostEstimate.Request(
                List.of(new EditableCostEstimate.Request.PersonnelInput(
                        "E1", "응용 SW 개발자", "백엔드 개발자", 1,
                        new BigDecimal("2"), new BigDecimal("50"), 8_000_000L
                )),
                new BigDecimal("30"), new BigDecimal("10"), 100_000L,
                List.of(new EditableCostEstimate.Request.ExpenseItemInput(
                        "클라우드", new BigDecimal("2"), "개월", 500_000L, true
                )),
                40_000L, true, "최종안"
        );

        EditableCostEstimate.Response result = service.calculate(1L, request);

        assertThat(result.personnel().getFirst().calculatedMm()).isEqualByComparingTo("1.00");
        assertThat(result.directLaborCost()).isEqualTo(8_000_000L);
        assertThat(result.overheadAmount()).isEqualTo(2_400_000L);
        assertThat(result.technicalFeeAmount()).isEqualTo(1_040_000L);
        assertThat(result.developmentCost()).isEqualTo(11_540_000L);
        assertThat(result.expenseItemTotal()).isEqualTo(1_000_000L);
        assertThat(result.supplyAmount()).isEqualTo(12_500_000L);
        assertThat(result.vat()).isEqualTo(1_250_000L);
        assertThat(result.totalAmount()).isEqualTo(13_750_000L);
    }
}
