package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CostEstimateRequest;
import com.aivle26.aipm.Dto.project.CostEstimateResponse;
import com.aivle26.aipm.Dto.project.FinalCostEstimateResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectCostEstimate;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FinalCostEstimateService {

    private static final boolean DEFAULT_USES_AI_API = false;
    private static final int DEFAULT_PAID_LICENSE_USER_COUNT = 0;
    private static final boolean DEFAULT_INCLUDE_VAT = true;

    private final CostEstimateService costEstimateService;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectCostEstimateRepository costEstimateRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public FinalCostEstimateResponse saveFinal(
            Long projectId,
            CostEstimateRequest request
    ) {
        CostEstimateResponse calculated = costEstimateService.estimate(projectId, request);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        ProjectCostEstimate entity = costEstimateRepository.findByProjectId(projectId)
                .orElseGet(ProjectCostEstimate::new);
        applyRequest(entity, project, request);
        applyCalculatedResult(entity, calculated);
        ProjectCostEstimate saved = costEstimateRepository.saveAndFlush(entity);
        return toResponse(saved, request.wbsEfforts(), calculated);
    }

    @Transactional(readOnly = true)
    public FinalCostEstimateResponse getFinal(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        ProjectCostEstimate entity = costEstimateRepository.findByProjectId(projectId)
                .filter(ProjectCostEstimate::isConfirmed)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "FINAL_COST_ESTIMATE_NOT_FOUND",
                        "Final cost estimate was not found."
                ));
        List<CostEstimateRequest.WbsEffort> wbsEfforts = readJson(
                entity.getWbsEffortsJson(),
                new TypeReference<List<CostEstimateRequest.WbsEffort>>() {
                }
        );
        List<String> unpricedItems = readJson(
                entity.getUnpricedItemsJson(),
                new TypeReference<List<String>>() {
                }
        );
        return toResponse(entity, wbsEfforts, calculatedFrom(entity, unpricedItems));
    }

    private void applyRequest(
            ProjectCostEstimate entity,
            Project project,
            CostEstimateRequest request
    ) {
        entity.setProject(project);
        entity.setWbsEffortsJson(writeJson(request.wbsEfforts()));
        entity.setAverageMonthlyUnitPrice(request.averageMonthlyUnitPrice());
        entity.setOperationMonths(request.operationMonths());
        entity.setServiceScale(request.serviceScale().name());
        entity.setUsesAiApi(request.usesAiApi() == null
                ? DEFAULT_USES_AI_API
                : request.usesAiApi());
        entity.setPaidLicenseUserCount(request.paidLicenseUserCount() == null
                ? DEFAULT_PAID_LICENSE_USER_COUNT
                : request.paidLicenseUserCount());
        entity.setIncludeVat(request.includeVat() == null
                ? DEFAULT_INCLUDE_VAT
                : request.includeVat());
        entity.setEditedEstimateJson(null);
        entity.setConfirmed(true);
    }

    private void applyCalculatedResult(
            ProjectCostEstimate entity,
            CostEstimateResponse result
    ) {
        CostEstimateResponse.CostSummary summary = result.costSummary();
        CostEstimateResponse.Estimate estimate = result.estimate();
        entity.setCurrency(result.currency());
        entity.setTotalEstimatedMm(result.totalEstimatedMm());
        entity.setLaborCost(summary.laborCost());
        entity.setServerCost(summary.serverCost());
        entity.setLicenseCost(summary.licenseCost());
        entity.setAiApiCost(summary.aiApiCost());
        entity.setBaseCost(summary.baseCost());
        entity.setContingencyRate(estimate.contingencyRate());
        entity.setContingencyAmount(estimate.contingencyAmount());
        entity.setSupplyAmount(estimate.supplyAmount());
        entity.setVat(estimate.vat());
        entity.setTotalAmount(estimate.totalAmount());
        entity.setUnpricedItemsJson(writeJson(result.unpricedItems()));
        entity.setWarning(result.warning());
        entity.setLlmStatus(result.llmStatus());
    }

    private FinalCostEstimateResponse toResponse(
            ProjectCostEstimate entity,
            List<CostEstimateRequest.WbsEffort> wbsEfforts,
            CostEstimateResponse calculated
    ) {
        return new FinalCostEstimateResponse(
                entity.getId(),
                entity.getProject().getId(),
                entity.isConfirmed(),
                List.copyOf(wbsEfforts),
                entity.getAverageMonthlyUnitPrice(),
                entity.getOperationMonths(),
                CostEstimateRequest.ServiceScale.valueOf(entity.getServiceScale()),
                entity.isUsesAiApi(),
                entity.getPaidLicenseUserCount(),
                entity.isIncludeVat(),
                calculated.currency(),
                calculated.totalEstimatedMm(),
                calculated.costSummary(),
                calculated.estimate(),
                calculated.unpricedItems(),
                calculated.warning(),
                calculated.llmStatus(),
                entity.getUpdatedAt()
        );
    }

    private CostEstimateResponse calculatedFrom(
            ProjectCostEstimate entity,
            List<String> unpricedItems
    ) {
        return new CostEstimateResponse(
                entity.getProject().getId(),
                entity.getCurrency(),
                entity.getTotalEstimatedMm(),
                new CostEstimateResponse.CostSummary(
                        entity.getLaborCost(),
                        entity.getServerCost(),
                        entity.getLicenseCost(),
                        entity.getAiApiCost(),
                        entity.getBaseCost()
                ),
                new CostEstimateResponse.Estimate(
                        entity.getContingencyRate(),
                        entity.getContingencyAmount(),
                        entity.getSupplyAmount(),
                        entity.getVat(),
                        entity.getTotalAmount()
                ),
                List.copyOf(unpricedItems),
                entity.getWarning(),
                entity.getLlmStatus()
        );
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "COST_ESTIMATE_DESERIALIZATION_ERROR",
                        "Stored final cost estimate is invalid."
                );
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "COST_ESTIMATE_DESERIALIZATION_ERROR",
                    "Stored final cost estimate could not be read.",
                    exception
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "COST_ESTIMATE_SERIALIZATION_ERROR",
                    "예상 견적을 저장할 수 없습니다.",
                    exception
            );
        }
    }
}
