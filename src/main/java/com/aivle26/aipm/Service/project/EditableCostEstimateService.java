package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.EditableCostEstimate;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectCostEstimate;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EditableCostEstimateService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");
    private static final String CURRENCY = "KRW";

    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final ProjectCostEstimateRepository costEstimateRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public EditableCostEstimate.Response calculate(
            Long projectId,
            EditableCostEstimate.Request request
    ) {
        authorizationService.requireProjectPm(projectId);
        requireProject(projectId);
        return calculateResult(projectId, request);
    }

    @Transactional
    public EditableCostEstimate.Response saveFinal(
            Long projectId,
            EditableCostEstimate.Request request
    ) {
        authorizationService.requireProjectPm(projectId);
        Project project = requireProject(projectId);
        EditableCostEstimate.Response calculated = calculateResult(projectId, request);
        ProjectCostEstimate entity = costEstimateRepository.findByProjectId(projectId)
                .orElseGet(ProjectCostEstimate::new);
        applyLegacyRequiredFields(entity, project, calculated);
        entity.setEditedEstimateJson(writeJson(calculated));
        ProjectCostEstimate saved = costEstimateRepository.saveAndFlush(entity);
        return withMetadata(calculated, saved);
    }

    @Transactional(readOnly = true)
    public EditableCostEstimate.Response getFinal(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        ProjectCostEstimate entity = costEstimateRepository.findByProjectId(projectId)
                .filter(ProjectCostEstimate::isConfirmed)
                .filter(cost -> cost.getEditedEstimateJson() != null && !cost.getEditedEstimateJson().isBlank())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EDITED_FINAL_COST_ESTIMATE_NOT_FOUND",
                        "저장된 편집 견적이 없습니다."
                ));
        try {
            EditableCostEstimate.Response stored = objectMapper.readValue(
                    entity.getEditedEstimateJson(),
                    EditableCostEstimate.Response.class
            );
            return withMetadata(stored, entity);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EDITED_COST_ESTIMATE_DESERIALIZATION_ERROR",
                    "저장된 편집 견적을 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private EditableCostEstimate.Response calculateResult(
            Long projectId,
            EditableCostEstimate.Request request
    ) {
        Map<String, User> assignedUsers = loadAssignedUsers(projectId);
        validatePersonnel(request.personnel(), assignedUsers.keySet());
        List<EditableCostEstimate.Response.Personnel> personnel = request.personnel().stream()
                .map(input -> calculatePersonnel(input, assignedUsers.get(input.employeeNumber())))
                .toList();
        List<EditableCostEstimate.Response.ExpenseItem> expenses = request.expenseItems() == null
                ? List.of()
                : request.expenseItems().stream().map(this::calculateExpense).toList();

        BigDecimal totalMm = personnel.stream()
                .map(EditableCostEstimate.Response.Personnel::calculatedMm)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long directLabor = sumMoney(personnel.stream().map(EditableCostEstimate.Response.Personnel::amount).toList());
        long overhead = percentage(directLabor, request.overheadRate());
        long technicalFee = percentage(Math.addExact(directLabor, overhead), request.technicalFeeRate());
        long developmentCost = Math.addExact(
                Math.addExact(Math.addExact(directLabor, overhead), technicalFee),
                request.directExpense()
        );
        long expenseTotal = sumMoney(expenses.stream()
                .filter(EditableCostEstimate.Response.ExpenseItem::included)
                .map(EditableCostEstimate.Response.ExpenseItem::amount)
                .toList());
        long beforeDiscount = Math.addExact(developmentCost, expenseTotal);
        if (request.discountAmount() > beforeDiscount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT_AMOUNT", "할인액은 공급가액을 초과할 수 없습니다.");
        }
        long supplyAmount = beforeDiscount - request.discountAmount();
        boolean includeVat = request.includeVat() == null || request.includeVat();
        long vat = includeVat ? money(BigDecimal.valueOf(supplyAmount).multiply(VAT_RATE)) : 0L;
        long totalAmount = Math.addExact(supplyAmount, vat);

        return new EditableCostEstimate.Response(
                null, projectId, false, KosaRates.RATE_YEAR, CURRENCY, personnel, expenses,
                totalMm, directLabor, request.overheadRate(), overhead,
                request.technicalFeeRate(), technicalFee, request.directExpense(), developmentCost,
                expenseTotal, request.discountAmount(), supplyAmount, includeVat, vat, totalAmount,
                normalizeNote(request.note()), null
        );
    }

    private Map<String, User> loadAssignedUsers(Long projectId) {
        Set<String> employeeNumbers = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId).stream()
                .map(ProjectTaskAssignment::getEmployeeNumber)
                .collect(Collectors.toSet());
        if (employeeNumbers.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "FINAL_ASSIGNMENTS_NOT_FOUND", "저장된 최종 담당자 배정이 없습니다.");
        }
        Map<String, User> users = userRepository.findAllById(employeeNumbers).stream()
                .collect(Collectors.toMap(User::getEmployeeNumber, Function.identity()));
        if (users.size() != employeeNumbers.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "ASSIGNED_USER_NOT_FOUND", "배정된 담당자 계정을 찾을 수 없습니다.");
        }
        return users;
    }

    private void validatePersonnel(
            List<EditableCostEstimate.Request.PersonnelInput> personnel,
            Set<String> assignedEmployeeNumbers
    ) {
        Set<String> keys = new HashSet<>();
        for (EditableCostEstimate.Request.PersonnelInput input : personnel) {
            if (!assignedEmployeeNumbers.contains(input.employeeNumber())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNASSIGNED_COST_PERSONNEL",
                        "프로젝트에 배정되지 않은 담당자입니다. employeeNumber=" + input.employeeNumber());
            }
            String key = input.employeeNumber() + "\u0000" + input.detailedJob();
            if (!keys.add(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_COST_PERSONNEL",
                        "동일 담당자와 세부직무가 중복되었습니다.");
            }
            KosaRates.monthlyRate(input.kosaJobCategory());
        }
    }

    private EditableCostEstimate.Response.Personnel calculatePersonnel(
            EditableCostEstimate.Request.PersonnelInput input,
            User user
    ) {
        BigDecimal mm = input.durationMonths()
                .multiply(BigDecimal.valueOf(input.headcount()))
                .multiply(input.utilizationRate())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        long standardRate = KosaRates.monthlyRate(input.kosaJobCategory());
        long amount = money(mm.multiply(BigDecimal.valueOf(input.proposedMonthlyRate())));
        return new EditableCostEstimate.Response.Personnel(
                input.employeeNumber(), user.getName(), input.kosaJobCategory(), input.detailedJob(),
                input.headcount(), input.durationMonths(), input.utilizationRate(), mm,
                standardRate, input.proposedMonthlyRate(), amount
        );
    }

    private EditableCostEstimate.Response.ExpenseItem calculateExpense(
            EditableCostEstimate.Request.ExpenseItemInput input
    ) {
        boolean included = input.included() == null || input.included();
        long amount = money(input.quantity().multiply(BigDecimal.valueOf(input.unitPrice())));
        return new EditableCostEstimate.Response.ExpenseItem(
                input.name().trim(), input.quantity(), input.unit().trim(), input.unitPrice(), included, amount
        );
    }

    private void applyLegacyRequiredFields(
            ProjectCostEstimate entity,
            Project project,
            EditableCostEstimate.Response result
    ) {
        entity.setProject(project);
        entity.setWbsEffortsJson("[]");
        entity.setAverageMonthlyUnitPrice(0L);
        entity.setOperationMonths(0);
        entity.setServiceScale("CUSTOM");
        entity.setUsesAiApi(true);
        entity.setPaidLicenseUserCount(0);
        entity.setIncludeVat(result.includeVat());
        entity.setCurrency(result.currency());
        entity.setTotalEstimatedMm(result.totalMm().doubleValue());
        entity.setLaborCost(result.directLaborCost());
        entity.setServerCost(0L);
        entity.setLicenseCost(result.expenseItemTotal());
        entity.setAiApiCost(0L);
        entity.setBaseCost(result.developmentCost() + result.expenseItemTotal());
        entity.setContingencyRate(0);
        entity.setContingencyAmount(0L);
        entity.setSupplyAmount(result.supplyAmount());
        entity.setVat(result.vat());
        entity.setTotalAmount(result.totalAmount());
        entity.setUnpricedItemsJson("[]");
        entity.setWarning("");
        entity.setLlmStatus("EDITED");
        entity.setConfirmed(true);
    }

    private EditableCostEstimate.Response withMetadata(
            EditableCostEstimate.Response response,
            ProjectCostEstimate entity
    ) {
        return new EditableCostEstimate.Response(
                entity.getId(), response.projectId(), entity.isConfirmed(), response.kosaRateYear(),
                response.currency(), response.personnel(), response.expenseItems(), response.totalMm(),
                response.directLaborCost(), response.overheadRate(), response.overheadAmount(),
                response.technicalFeeRate(), response.technicalFeeAmount(), response.directExpense(),
                response.developmentCost(), response.expenseItemTotal(), response.discountAmount(),
                response.supplyAmount(), response.includeVat(), response.vat(), response.totalAmount(),
                response.note(), entity.getUpdatedAt()
        );
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
    }

    private long percentage(long amount, BigDecimal rate) {
        return money(BigDecimal.valueOf(amount).multiply(rate).divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
    }

    private long sumMoney(List<Long> amounts) {
        return amounts.stream().reduce(0L, Math::addExact);
    }

    private long money(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COST_AMOUNT_OVERFLOW", "계산된 견적 금액이 허용 범위를 초과했습니다.", exception);
        }
    }

    private String normalizeNote(String note) {
        return note == null ? "" : note.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EDITED_COST_ESTIMATE_SERIALIZATION_ERROR",
                    "편집 견적을 저장할 수 없습니다.", exception);
        }
    }
}
