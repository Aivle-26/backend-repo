package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.KosaEffortEstimate;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.client.ai.PlanningCostClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KosaEffortEstimateService {

    private static final String CURRENCY = "KRW";
    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final PlanningCostClient planningCostClient;

    @Transactional(readOnly = true)
    public KosaEffortEstimate.Response estimate(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
        List<ProjectWbsTask> tasks = loadBillableTasks(projectId);
        Map<Long, ProjectSchedule> schedules = loadFinalSchedules(projectId);
        Map<Long, ProjectTaskAssignment> assignments = loadAssignments(projectId, tasks);
        Map<String, User> users = loadUsers(assignments.values());

        KosaEffortEstimate.AiResponse aiResponse = planningCostClient.estimateEffort(
                toAiRequest(project, tasks, schedules),
                "backend-project-" + projectId + "-cost-effort-" + UUID.randomUUID()
        );
        validateAiResponse(projectId, tasks, aiResponse);
        return toResponse(project, aiResponse, schedules, assignments, users);
    }

    private List<ProjectWbsTask> loadBillableTasks(Long projectId) {
        List<ProjectWbsTask> confirmed = wbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId);
        if (confirmed.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFIRMED_WBS_NOT_FOUND", "확정된 WBS가 없습니다.");
        }
        Set<Long> parentIds = confirmed.stream()
                .map(ProjectWbsTask::getParentTask)
                .filter(parent -> parent != null)
                .map(ProjectWbsTask::getId)
                .collect(Collectors.toSet());
        return confirmed.stream()
                .filter(task -> !parentIds.contains(task.getId()))
                .sorted(Comparator.comparingInt(ProjectWbsTask::getOrderIndex).thenComparing(ProjectWbsTask::getId))
                .toList();
    }

    private Map<Long, ProjectSchedule> loadFinalSchedules(Long projectId) {
        return scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId).stream()
                .filter(ProjectSchedule::isConfirmed)
                .collect(Collectors.toMap(
                        schedule -> schedule.getWbsTask().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, ProjectTaskAssignment> loadAssignments(
            Long projectId,
            List<ProjectWbsTask> tasks
    ) {
        Map<Long, ProjectTaskAssignment> assignments = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId).stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getWbsTask().getId(),
                        Function.identity()
                ));
        List<Long> unassignedWbsIds = tasks.stream()
                .map(ProjectWbsTask::getId)
                .filter(wbsId -> !assignments.containsKey(wbsId))
                .toList();
        if (!unassignedWbsIds.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "UNASSIGNED_WBS_EXISTS",
                    "담당자가 배정되지 않은 WBS가 있습니다. wbsIds=" + unassignedWbsIds
            );
        }
        return assignments;
    }

    private Map<String, User> loadUsers(Iterable<ProjectTaskAssignment> assignments) {
        List<String> employeeNumbers = new ArrayList<>();
        assignments.forEach(assignment -> employeeNumbers.add(assignment.getEmployeeNumber()));
        Map<String, User> users = userRepository.findAllById(employeeNumbers).stream()
                .collect(Collectors.toMap(User::getEmployeeNumber, Function.identity()));
        List<String> missing = employeeNumbers.stream().distinct().filter(number -> !users.containsKey(number)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ASSIGNED_USER_NOT_FOUND",
                    "배정된 담당자 계정을 찾을 수 없습니다. employeeNumbers=" + missing
            );
        }
        return users;
    }

    private KosaEffortEstimate.AiRequest toAiRequest(
            Project project,
            List<ProjectWbsTask> tasks,
            Map<Long, ProjectSchedule> schedules
    ) {
        return new KosaEffortEstimate.AiRequest(
                project.getId(),
                project.getName(),
                tasks.stream().map(task -> {
                    ProjectSchedule schedule = schedules.get(task.getId());
                    return new KosaEffortEstimate.AiRequest.WbsTask(
                            task.getId(),
                            task.getTaskName(),
                            task.getDescription(),
                            schedule == null ? null : schedule.getStartDate(),
                            schedule == null ? null : schedule.getEndDate()
                    );
                }).toList()
        );
    }

    private void validateAiResponse(
            Long projectId,
            List<ProjectWbsTask> tasks,
            KosaEffortEstimate.AiResponse response
    ) {
        Set<Long> expectedIds = tasks.stream().map(ProjectWbsTask::getId).collect(Collectors.toSet());
        if (response == null
                || !projectId.equals(response.projectId())
                || response.workdaysPerMonth() == null
                || response.workdaysPerMonth().signum() <= 0
                || response.wbsEfforts() == null
                || response.totalEstimatedPersonDays() == null
                || response.totalEstimatedMm() == null
                || response.llmStatus() == null
                || response.llmStatus().isBlank()) {
            throw invalidAiResponse();
        }
        Set<Long> actualIds = response.wbsEfforts().stream()
                .map(KosaEffortEstimate.AiResponse.WbsEffort::wbsId)
                .collect(Collectors.toSet());
        boolean invalidEffort = response.wbsEfforts().stream().anyMatch(effort ->
                effort.wbsId() == null
                        || effort.estimatedPersonDays() == null
                        || effort.estimatedPersonDays().signum() < 0
                        || effort.estimatedMm() == null
                        || effort.estimatedMm().signum() < 0
                        || effort.confidence() == null
                        || effort.confidence().compareTo(BigDecimal.ZERO) < 0
                        || effort.confidence().compareTo(BigDecimal.ONE) > 0
                        || !KosaRates.supports(effort.kosaJobCategory())
                        || effort.detailedJob() == null
                        || effort.detailedJob().isBlank());
        if (!actualIds.equals(expectedIds)
                || response.wbsEfforts().size() != expectedIds.size()
                || invalidEffort) {
            throw invalidAiResponse();
        }
    }

    private KosaEffortEstimate.Response toResponse(
            Project project,
            KosaEffortEstimate.AiResponse aiResponse,
            Map<Long, ProjectSchedule> schedules,
            Map<Long, ProjectTaskAssignment> assignments,
            Map<String, User> users
    ) {
        List<KosaEffortEstimate.Response.WbsEvidence> evidence = aiResponse.wbsEfforts().stream()
                .map(effort -> toEvidence(effort, assignments.get(effort.wbsId()), users))
                .toList();
        Map<PersonnelKey, List<KosaEffortEstimate.Response.WbsEvidence>> grouped = evidence.stream()
                .collect(Collectors.groupingBy(
                        item -> new PersonnelKey(item.employeeNumber(), item.detailedJob(), item.kosaJobCategory()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<KosaEffortEstimate.Response.Personnel> personnel = grouped.entrySet().stream()
                .map(entry -> toPersonnel(entry.getKey(), entry.getValue(), users, aiResponse.workdaysPerMonth()))
                .toList();
        long totalPersonnelAmount = personnel.stream()
                .mapToLong(KosaEffortEstimate.Response.Personnel::amount)
                .sum();
        LocalDate projectStart = schedules.values().stream()
                .map(ProjectSchedule::getStartDate)
                .min(LocalDate::compareTo)
                .orElse(project.getPlannedStartDate());
        LocalDate projectEnd = schedules.values().stream()
                .map(ProjectSchedule::getEndDate)
                .max(LocalDate::compareTo)
                .orElse(project.getPlannedEndDate());
        return new KosaEffortEstimate.Response(
                project.getId(), project.getName(), projectStart, projectEnd, KosaRates.RATE_YEAR,
                aiResponse.workdaysPerMonth(), aiResponse.totalEstimatedPersonDays(),
                aiResponse.totalEstimatedMm(), totalPersonnelAmount, CURRENCY,
                aiResponse.llmStatus(), personnel, evidence
        );
    }

    private KosaEffortEstimate.Response.WbsEvidence toEvidence(
            KosaEffortEstimate.AiResponse.WbsEffort effort,
            ProjectTaskAssignment assignment,
            Map<String, User> users
    ) {
        User user = users.get(assignment.getEmployeeNumber());
        return new KosaEffortEstimate.Response.WbsEvidence(
                effort.wbsId(), effort.wbsName(), user.getEmployeeNumber(), user.getName(),
                effort.kosaJobCategory(), effort.detailedJob(), effort.estimatedPersonDays(),
                effort.estimatedMm(), effort.estimationReason(), effort.confidence()
        );
    }

    private KosaEffortEstimate.Response.Personnel toPersonnel(
            PersonnelKey key,
            List<KosaEffortEstimate.Response.WbsEvidence> evidence,
            Map<String, User> users,
            BigDecimal workdaysPerMonth
    ) {
        BigDecimal personDays = evidence.stream()
                .map(KosaEffortEstimate.Response.WbsEvidence::estimatedPersonDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mm = personDays.divide(workdaysPerMonth, 2, RoundingMode.HALF_UP);
        long rate = KosaRates.monthlyRate(key.kosaJobCategory());
        long amount = mm.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new KosaEffortEstimate.Response.Personnel(
                key.employeeNumber(), users.get(key.employeeNumber()).getName(), key.kosaJobCategory(),
                key.detailedJob(), personDays, mm, 1, mm, BigDecimal.valueOf(100),
                rate, rate, amount, List.copyOf(evidence)
        );
    }

    private ApiException invalidAiResponse() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_EFFORT_RESPONSE",
                "AI Server의 공수 산정 결과 형식이 올바르지 않습니다."
        );
    }

    private record PersonnelKey(String employeeNumber, String detailedJob, String kosaJobCategory) {
    }
}
