package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CostEstimateRequest;
import com.aivle26.aipm.Dto.project.CostEstimateResponse;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateRequest;
import com.aivle26.aipm.Dto.project.PlanningCostEstimateResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.PlanningCostClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostEstimateService {

    private static final boolean DEFAULT_USES_AI_API = false;
    private static final int DEFAULT_PAID_LICENSE_USER_COUNT = 0;
    private static final boolean DEFAULT_INCLUDE_VAT = true;
    private static final String KRW = "KRW";

    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final PlanningCostClient planningCostClient;

    @Transactional(readOnly = true)
    public CostEstimateResponse estimate(Long projectId, CostEstimateRequest request) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
        List<ProjectWbsTask> billableTasks = loadBillableTasks(projectId);
        Map<Long, Double> estimatedMmByWbsId = validateAndIndexEfforts(
                request.wbsEfforts(),
                billableTasks
        );

        PlanningCostEstimateRequest aiRequest = toAiRequest(
                project,
                request,
                billableTasks,
                estimatedMmByWbsId
        );
        PlanningCostEstimateResponse aiResponse = planningCostClient.estimate(aiRequest);
        validateAiResponse(projectId, aiResponse);
        return toResponse(aiResponse);
    }

    private List<ProjectWbsTask> loadBillableTasks(Long projectId) {
        List<ProjectWbsTask> confirmedTasks = new ArrayList<>(
                wbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId)
        );
        if (confirmedTasks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        Set<Long> parentIds = confirmedTasks.stream()
                .map(ProjectWbsTask::getParentTask)
                .filter(parent -> parent != null)
                .map(ProjectWbsTask::getId)
                .collect(Collectors.toSet());
        List<ProjectWbsTask> billableTasks = confirmedTasks.stream()
                .filter(task -> !parentIds.contains(task.getId()))
                .sorted(Comparator.comparingInt(ProjectWbsTask::getOrderIndex)
                        .thenComparing(ProjectWbsTask::getId))
                .toList();
        if (billableTasks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "billable wbs task not found");
        }
        return billableTasks;
    }

    private Map<Long, Double> validateAndIndexEfforts(
            List<CostEstimateRequest.WbsEffort> requestedEfforts,
            List<ProjectWbsTask> billableTasks
    ) {
        Map<Long, Double> estimatedMmByWbsId = new LinkedHashMap<>();
        for (CostEstimateRequest.WbsEffort effort : requestedEfforts) {
            if (estimatedMmByWbsId.putIfAbsent(effort.wbsId(), effort.estimatedMm()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate wbs effort");
            }
        }
        Set<Long> billableWbsIds = billableTasks.stream()
                .map(ProjectWbsTask::getId)
                .collect(Collectors.toSet());
        if (!estimatedMmByWbsId.keySet().equals(billableWbsIds)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "wbs efforts must contain every billable wbs task exactly once"
            );
        }
        return estimatedMmByWbsId;
    }

    private PlanningCostEstimateRequest toAiRequest(
            Project project,
            CostEstimateRequest request,
            List<ProjectWbsTask> billableTasks,
            Map<Long, Double> estimatedMmByWbsId
    ) {
        List<PlanningCostEstimateRequest.WbsEffort> efforts = billableTasks.stream()
                .map(task -> new PlanningCostEstimateRequest.WbsEffort(
                        task.getId(),
                        task.getTaskName(),
                        task.getDescription(),
                        estimatedMmByWbsId.get(task.getId())
                ))
                .toList();
        return new PlanningCostEstimateRequest(
                project.getId(),
                project.getName(),
                efforts,
                request.averageMonthlyUnitPrice(),
                request.operationMonths(),
                request.serviceScale(),
                request.usesAiApi() == null
                        ? DEFAULT_USES_AI_API
                        : request.usesAiApi(),
                request.paidLicenseUserCount() == null
                        ? DEFAULT_PAID_LICENSE_USER_COUNT
                        : request.paidLicenseUserCount(),
                request.includeVat() == null
                        ? DEFAULT_INCLUDE_VAT
                        : request.includeVat()
        );
    }

    private void validateAiResponse(
            Long projectId,
            PlanningCostEstimateResponse response
    ) {
        if (response == null
                || !projectId.equals(response.projectId())
                || !KRW.equals(response.currency())
                || response.totalEstimatedMm() <= 0
                || response.costSummary() == null
                || response.estimate() == null
                || response.unpricedItems() == null
                || response.warning() == null
                || response.llmStatus() == null
                || response.llmStatus().isBlank()
                || hasNegativeAmount(response)) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_PLANNING_COST_RESPONSE",
                    "AI Server의 예상 견적 결과 형식이 올바르지 않습니다."
            );
        }
    }

    private boolean hasNegativeAmount(PlanningCostEstimateResponse response) {
        PlanningCostEstimateResponse.CostSummary summary = response.costSummary();
        PlanningCostEstimateResponse.Estimate estimate = response.estimate();
        return summary.laborCost() < 0
                || summary.serverCost() < 0
                || summary.licenseCost() < 0
                || summary.aiApiCost() < 0
                || summary.baseCost() < 0
                || estimate.contingencyRate() < 0
                || estimate.contingencyRate() > 100
                || estimate.contingencyAmount() < 0
                || estimate.supplyAmount() < 0
                || estimate.vat() < 0
                || estimate.totalAmount() < 0;
    }

    private CostEstimateResponse toResponse(PlanningCostEstimateResponse response) {
        PlanningCostEstimateResponse.CostSummary summary = response.costSummary();
        PlanningCostEstimateResponse.Estimate estimate = response.estimate();
        return new CostEstimateResponse(
                response.projectId(),
                response.currency(),
                response.totalEstimatedMm(),
                new CostEstimateResponse.CostSummary(
                        summary.laborCost(),
                        summary.serverCost(),
                        summary.licenseCost(),
                        summary.aiApiCost(),
                        summary.baseCost()
                ),
                new CostEstimateResponse.Estimate(
                        estimate.contingencyRate(),
                        estimate.contingencyAmount(),
                        estimate.supplyAmount(),
                        estimate.vat(),
                        estimate.totalAmount()
                ),
                List.copyOf(response.unpricedItems()),
                response.warning(),
                response.llmStatus()
        );
    }
}
