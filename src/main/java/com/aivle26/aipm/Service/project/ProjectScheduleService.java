package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.AgentRequestResult;
import com.aivle26.aipm.Dto.project.PlanningScheduleRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningScheduleRecommendResponse;
import com.aivle26.aipm.Dto.project.ProjectScheduleResponse;
import com.aivle26.aipm.Dto.project.SaveScheduleResultRequest;
import com.aivle26.aipm.Dto.project.SaveScheduleResultResponse;
import com.aivle26.aipm.Dto.project.SaveFinalScheduleRequest;
import com.aivle26.aipm.Dto.project.ScheduleResultRequest;
import com.aivle26.aipm.Entity.project.AgentExecutionStatus;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectScheduleResult;
import com.aivle26.aipm.Entity.project.ProjectScheduleScenario;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.ScheduleScenarioType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.PlanningScheduleClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectScheduleService {
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectScheduleResultRepository projectScheduleResultRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final PlanningScheduleClient planningScheduleClient;
    private final PlanningAgentProperties planningAgentProperties;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ObjectMapper objectMapper;

    // 확정 WBS로 Planning AI를 호출하고 세 일정 시나리오를 원자적으로 저장한다.
    @Transactional
    public AgentRequestResult requestScheduleGeneration(Long projectId) {
        Project project = requireSchedulableProject(projectId);
        List<ProjectWbsTask> confirmedWbsTasks = getConfirmedWbsTasks(projectId);
        PlanningScheduleRecommendRequest aiRequest = toAiRequest(project, confirmedWbsTasks);

        PlanningScheduleRecommendResponse aiResponse =
                planningScheduleClient.recommendSchedules(aiRequest);

        String generatedExecutionId = "schedule-" + UUID.randomUUID();
        String agentExecutionId = normalizeOrDefault(
                aiResponse.agentExecutionId(),
                generatedExecutionId
        );
        String agentVersion = normalizeOrDefault(
                aiResponse.agentVersion(),
                planningAgentProperties.getScheduleAgentVersion()
        );

        if (projectScheduleResultRepository.existsByAgentExecutionId(agentExecutionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }

        Map<Long, ProjectWbsTask> wbsTaskById = toWbsTaskMap(confirmedWbsTasks);
        List<PreparedSchedule> preparedSchedules = prepareAiSchedules(
                project,
                aiResponse,
                wbsTaskById,
                agentExecutionId
        );

        persistSchedules(
                project,
                agentExecutionId,
                agentVersion,
                project.getPlannedStartDate(),
                project.getPlannedEndDate(),
                preparedSchedules,
                withOverrunWarnings(
                        safeWarnings(aiResponse.warnings()),
                        preparedSchedules,
                        project.getPlannedEndDate()
                ),
                aiResponse.llmStatus(),
                false
        );

        return new AgentRequestResult(
                agentExecutionId,
                AgentExecutionStatus.SUCCEEDED,
                agentVersion
        );
    }

    // 기존 단일 일정 결과 API를 유지하되 같은 날짜로 세 시나리오를 영구 저장한다.
    @Transactional
    public SaveScheduleResultResponse saveScheduleResult(
            Long projectId,
            SaveScheduleResultRequest request
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getProject(projectId);
        validateProjectState(project);

        if (!projectWbsTaskRepository.existsByProjectIdAndConfirmedTrue(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        String agentExecutionId = request.agentExecutionId().trim();
        if (projectScheduleResultRepository.existsByAgentExecutionId(agentExecutionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }
        if (projectScheduleResultRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "schedule already exists");
        }

        validateProjectWindow(project, request.projectStartDate(), request.targetEndDate());
        Map<Long, ProjectWbsTask> wbsTaskById =
                toWbsTaskMap(getConfirmedWbsTasks(projectId));
        validateLegacySchedules(
                project,
                request.schedules(),
                wbsTaskById,
                request.projectStartDate(),
                request.targetEndDate()
        );

        List<PreparedSchedule> preparedSchedules = request.schedules().stream()
                .map(schedule -> toLegacyPreparedSchedule(schedule, wbsTaskById))
                .toList();

        ProjectScheduleResult savedResult = persistSchedules(
                project,
                agentExecutionId,
                request.agentVersion().trim(),
                request.projectStartDate(),
                request.targetEndDate(),
                preparedSchedules,
                withOverrunWarnings(List.of(), preparedSchedules, request.targetEndDate()),
                null,
                false
        );

        return new SaveScheduleResultResponse(
                savedResult.getId(),
                projectId,
                savedResult.getAgentExecutionId(),
                preparedSchedules.size()
        );
    }

    // 저장된 일정과 WBS별 P50·P80·P90 시나리오를 함께 조회한다.
    @Transactional(readOnly = true)
    public ProjectScheduleResponse getSchedules(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        getProject(projectId);

        ProjectScheduleResult result = projectScheduleResultRepository
                .findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "schedule not found"));
        List<ProjectSchedule> schedules =
                projectScheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId);

        return new ProjectScheduleResponse(
                result.getId(),
                projectId,
                result.getAgentExecutionId(),
                result.getAgentVersion(),
                result.getLlmStatus(),
                result.getProjectStartDate(),
                result.getTargetEndDate(),
                toScheduleDetails(schedules),
                readWarnings(result.getWarningsJson())
        );
    }

    @Transactional
    public ProjectScheduleResponse saveFinalSchedule(
            Long projectId,
            SaveFinalScheduleRequest request
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getProject(projectId);
        validateProjectState(project);
        validateProjectWindow(project, request.projectStartDate(), request.targetEndDate());

        ProjectScheduleResult result = projectScheduleResultRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "schedule not found"));
        Map<Long, ProjectWbsTask> wbsTaskById = toWbsTaskMap(getConfirmedWbsTasks(projectId));
        List<PreparedSchedule> preparedSchedules = prepareFinalSchedules(
                project,
                request,
                wbsTaskById
        );

        projectScheduleRepository.deletePredecessorLinksByProjectId(projectId);
        projectScheduleRepository.deleteAllByProjectId(projectId);
        projectScheduleRepository.flush();

        result.setProjectStartDate(request.projectStartDate());
        result.setTargetEndDate(request.targetEndDate());
        result.setWarningsJson(writeWarnings(withOverrunWarnings(
                readWarnings(result.getWarningsJson()),
                preparedSchedules,
                request.targetEndDate()
        )));
        ProjectScheduleResult savedResult = projectScheduleResultRepository.save(result);
        persistScheduleItems(savedResult, preparedSchedules, true);
        return getSchedules(projectId);
    }

    private Project requireSchedulableProject(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getProject(projectId);
        validateProjectState(project);

        if (project.getPlannedStartDate() == null || project.getPlannedEndDate() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "project schedule date not found");
        }
        if (project.getPlannedEndDate().isBefore(project.getPlannedStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }
        if (projectScheduleResultRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "schedule already exists");
        }
        if (!projectWbsTaskRepository.existsByProjectIdAndConfirmedTrue(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        return project;
    }

    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
    }

    private void validateProjectState(Project project) {
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
    }

    private List<ProjectWbsTask> getConfirmedWbsTasks(Long projectId) {
        List<ProjectWbsTask> tasks =
                new ArrayList<>(projectWbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId));
        if (tasks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        tasks.sort(Comparator.comparingInt(ProjectWbsTask::getOrderIndex)
                .thenComparing(ProjectWbsTask::getId));
        return tasks;
    }

    private Map<Long, ProjectWbsTask> toWbsTaskMap(List<ProjectWbsTask> tasks) {
        Map<Long, ProjectWbsTask> wbsTaskById = new LinkedHashMap<>();
        for (ProjectWbsTask task : tasks) {
            wbsTaskById.put(task.getId(), task);
        }
        return wbsTaskById;
    }

    private PlanningScheduleRecommendRequest toAiRequest(
            Project project,
            List<ProjectWbsTask> tasks
    ) {
        Map<Long, ProjectWbsTask> taskById = toWbsTaskMap(tasks);
        Set<Long> parentIds = new LinkedHashSet<>();
        for (ProjectWbsTask task : tasks) {
            if (task.getParentTask() != null) {
                Long parentId = task.getParentTask().getId();
                if (!taskById.containsKey(parentId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "invalid wbs hierarchy");
                }
                parentIds.add(parentId);
            }
        }

        List<PlanningScheduleRecommendRequest.ScheduleWbsItem> items = tasks.stream()
                .map(task -> new PlanningScheduleRecommendRequest.ScheduleWbsItem(
                        task.getId(),
                        task.getTaskCode(),
                        task.getParentTask() == null ? null : task.getParentTask().getId(),
                        resolveItemType(task, parentIds),
                        task.getTaskName(),
                        task.getDescription()
                ))
                .toList();

        return new PlanningScheduleRecommendRequest(
                project.getId(),
                project.getPlannedStartDate(),
                project.getPlannedEndDate(),
                items
        );
    }

    private PlanningScheduleRecommendRequest.ItemType resolveItemType(
            ProjectWbsTask task,
            Set<Long> parentIds
    ) {
        if (task.getParentTask() == null) {
            return PlanningScheduleRecommendRequest.ItemType.PHASE;
        }
        if (parentIds.contains(task.getId())) {
            return PlanningScheduleRecommendRequest.ItemType.WORK_PACKAGE;
        }
        return PlanningScheduleRecommendRequest.ItemType.TASK;
    }

    private List<PreparedSchedule> prepareAiSchedules(
            Project project,
            PlanningScheduleRecommendResponse response,
            Map<Long, ProjectWbsTask> wbsTaskById,
            String agentExecutionId
    ) {
        if (response == null
                || response.projectId() == null
                || !project.getId().equals(response.projectId())) {
            throw invalidAiResponse("invalid project id");
        }
        if (response.wbsSchedules() == null) {
            throw invalidAiResponse("schedule not found");
        }

        Map<Long, PreparedSchedule> preparedByWbsId = new LinkedHashMap<>();
        Set<String> externalScheduleIds = new LinkedHashSet<>();
        for (PlanningScheduleRecommendResponse.WbsSchedule aiSchedule : response.wbsSchedules()) {
            if (aiSchedule == null || aiSchedule.wbsId() == null) {
                throw invalidAiResponse("invalid wbs id");
            }
            ProjectWbsTask wbsTask = wbsTaskById.get(aiSchedule.wbsId());
            if (wbsTask == null) {
                throw invalidAiResponse("invalid wbs id");
            }
            if (preparedByWbsId.containsKey(aiSchedule.wbsId())) {
                throw invalidAiResponse("duplicate wbs schedule");
            }

            ScenarioRange expected = toScenarioRange(
                    project,
                    ScheduleScenarioType.EXPECTED,
                    aiSchedule.expected()
            );
            ScenarioRange recommended = toScenarioRange(
                    project,
                    ScheduleScenarioType.RECOMMENDED,
                    aiSchedule.recommended()
            );
            ScenarioRange conservative = toScenarioRange(
                    project,
                    ScheduleScenarioType.CONSERVATIVE,
                    aiSchedule.conservative()
            );

            String externalScheduleId = normalizeOrDefault(
                    aiSchedule.externalScheduleId(),
                    agentExecutionId + "-" + aiSchedule.wbsId()
            );
            if (!externalScheduleIds.add(externalScheduleId)) {
                throw invalidAiResponse("duplicate schedule external id");
            }

            PreparedSchedule prepared = new PreparedSchedule(
                    externalScheduleId,
                    wbsTask,
                    expected,
                    recommended,
                    conservative,
                    safeIds(aiSchedule.predecessorWbsIds()),
                    Boolean.TRUE.equals(aiSchedule.milestone()),
                    aiSchedule.bufferDays() == null ? 0 : aiSchedule.bufferDays()
            );
            if (prepared.bufferDays() < 0) {
                throw invalidAiResponse("invalid buffer days");
            }
            preparedByWbsId.put(aiSchedule.wbsId(), prepared);
        }

        if (!preparedByWbsId.keySet().equals(wbsTaskById.keySet())) {
            throw invalidAiResponse("wbs schedule missing");
        }

        List<PreparedSchedule> preparedSchedules =
                new ArrayList<>(preparedByWbsId.values());
        validatePreparedPredecessors(preparedSchedules, wbsTaskById);
        return preparedSchedules;
    }

    private List<PreparedSchedule> prepareFinalSchedules(
            Project project,
            SaveFinalScheduleRequest request,
            Map<Long, ProjectWbsTask> wbsTaskById
    ) {
        List<PlanningScheduleRecommendResponse.WbsSchedule> schedules = request.schedules().stream()
                .map(item -> new PlanningScheduleRecommendResponse.WbsSchedule(
                        item.wbsId(),
                        toAiRange(item.expected()),
                        toAiRange(item.recommended()),
                        toAiRange(item.conservative()),
                        item.predecessorWbsIds(),
                        item.milestone(),
                        item.bufferDays(),
                        item.externalScheduleId()
                ))
                .toList();
        return prepareAiSchedules(
                project,
                new PlanningScheduleRecommendResponse(
                        project.getId(), schedules, List.of(), null, null, null
                ),
                wbsTaskById,
                "schedule-final"
        );
    }

    private PlanningScheduleRecommendResponse.ScheduleDateRange toAiRange(
            SaveFinalScheduleRequest.DateRange range
    ) {
        return new PlanningScheduleRecommendResponse.ScheduleDateRange(
                range.startDate(), range.endDate()
        );
    }

    private ScenarioRange toScenarioRange(
            Project project,
            ScheduleScenarioType type,
            PlanningScheduleRecommendResponse.ScheduleDateRange range
    ) {
        if (range == null || range.startDate() == null || range.endDate() == null) {
            throw invalidAiResponse(type.name().toLowerCase() + " schedule missing");
        }
        validateScheduleDateRangeAllowOverrun(
                project,
                range.startDate(),
                range.endDate()
        );
        return new ScenarioRange(
                type,
                range.startDate(),
                range.endDate(),
                calculateEstimatedDays(range.startDate(), range.endDate())
        );
    }

    private PreparedSchedule toLegacyPreparedSchedule(
            ScheduleResultRequest request,
            Map<Long, ProjectWbsTask> wbsTaskById
    ) {
        ScenarioRange expected = new ScenarioRange(
                ScheduleScenarioType.EXPECTED,
                request.startDate(),
                request.endDate(),
                request.estimatedDays()
        );
        ScenarioRange recommended = new ScenarioRange(
                ScheduleScenarioType.RECOMMENDED,
                request.startDate(),
                request.endDate(),
                request.estimatedDays()
        );
        ScenarioRange conservative = new ScenarioRange(
                ScheduleScenarioType.CONSERVATIVE,
                request.startDate(),
                request.endDate(),
                request.estimatedDays()
        );
        return new PreparedSchedule(
                request.externalScheduleId().trim(),
                wbsTaskById.get(request.wbsId()),
                expected,
                recommended,
                conservative,
                List.copyOf(request.predecessorWbsIds()),
                request.milestone(),
                request.bufferDays()
        );
    }

    private ProjectScheduleResult persistSchedules(
            Project project,
            String agentExecutionId,
            String agentVersion,
            LocalDate projectStartDate,
            LocalDate targetEndDate,
            List<PreparedSchedule> preparedSchedules,
            List<String> warnings,
            String llmStatus,
            boolean confirmed
    ) {
        ProjectScheduleResult scheduleResult = new ProjectScheduleResult();
        scheduleResult.setProject(project);
        scheduleResult.setAgentExecutionId(agentExecutionId);
        scheduleResult.setAgentVersion(agentVersion);
        scheduleResult.setLlmStatus(normalizeNullable(llmStatus));
        scheduleResult.setProjectStartDate(projectStartDate);
        scheduleResult.setTargetEndDate(targetEndDate);
        scheduleResult.setWarningsJson(writeWarnings(warnings));
        ProjectScheduleResult savedResult =
                projectScheduleResultRepository.save(scheduleResult);

        persistScheduleItems(savedResult, preparedSchedules, confirmed);
        return savedResult;
    }

    private void persistScheduleItems(
            ProjectScheduleResult savedResult,
            List<PreparedSchedule> preparedSchedules,
            boolean confirmed
    ) {
        Project project = savedResult.getProject();
        Map<Long, ProjectSchedule> scheduleByWbsId = new LinkedHashMap<>();
        for (PreparedSchedule prepared : preparedSchedules) {
            ProjectSchedule schedule = new ProjectSchedule();
            schedule.setProject(project);
            schedule.setScheduleResult(savedResult);
            schedule.setWbsTask(prepared.wbsTask());
            schedule.setExternalScheduleId(prepared.externalScheduleId());
            schedule.setStartDate(prepared.recommended().startDate());
            schedule.setEndDate(prepared.recommended().endDate());
            schedule.setEstimatedDays(prepared.recommended().estimatedDays());
            schedule.setMilestone(prepared.milestone());
            schedule.setBufferDays(prepared.bufferDays());
            schedule.setConfirmed(confirmed);
            schedule.addScenario(toScenarioEntity(prepared.expected()));
            schedule.addScenario(toScenarioEntity(prepared.recommended()));
            schedule.addScenario(toScenarioEntity(prepared.conservative()));
            scheduleByWbsId.put(prepared.wbsTask().getId(), schedule);
        }

        projectScheduleRepository.saveAll(scheduleByWbsId.values());
        projectScheduleRepository.flush();

        for (PreparedSchedule prepared : preparedSchedules) {
            ProjectSchedule schedule =
                    scheduleByWbsId.get(prepared.wbsTask().getId());
            Set<ProjectSchedule> predecessors = new LinkedHashSet<>();
            for (Long predecessorWbsId : prepared.predecessorWbsIds()) {
                predecessors.add(scheduleByWbsId.get(predecessorWbsId));
            }
            schedule.setPredecessors(predecessors);
        }
        projectScheduleRepository.saveAll(scheduleByWbsId.values());
        projectScheduleRepository.flush();
    }

    private ProjectScheduleScenario toScenarioEntity(ScenarioRange range) {
        ProjectScheduleScenario scenario = new ProjectScheduleScenario();
        scenario.setScenarioType(range.type());
        scenario.setStartDate(range.startDate());
        scenario.setEndDate(range.endDate());
        scenario.setEstimatedDays(range.estimatedDays());
        return scenario;
    }

    private void validateProjectWindow(
            Project project,
            LocalDate projectStartDate,
            LocalDate targetEndDate
    ) {
        if (projectStartDate == null || targetEndDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project schedule date not found");
        }
        if (targetEndDate.isBefore(projectStartDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }
        if (project.getPlannedStartDate() != null
                && projectStartDate.isBefore(project.getPlannedStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
        if (project.getPlannedEndDate() != null
                && targetEndDate.isAfter(project.getPlannedEndDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
    }

    private void validateLegacySchedules(
            Project project,
            List<ScheduleResultRequest> schedules,
            Map<Long, ProjectWbsTask> wbsTaskById,
            LocalDate projectStartDate,
            LocalDate targetEndDate
    ) {
        if (schedules == null || schedules.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "schedule not found");
        }
        Map<Long, List<Long>> predecessorsByWbsId = new LinkedHashMap<>();
        Set<Long> scheduleWbsIds = new LinkedHashSet<>();
        Set<String> externalScheduleIds = new LinkedHashSet<>();

        for (ScheduleResultRequest schedule : schedules) {
            if (!externalScheduleIds.add(schedule.externalScheduleId().trim())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate schedule external id");
            }
            if (!scheduleWbsIds.add(schedule.wbsId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "schedule conflict");
            }
            if (!wbsTaskById.containsKey(schedule.wbsId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid wbs id");
            }
            validateScheduleDateRange(
                    project,
                    schedule.startDate(),
                    schedule.endDate(),
                    projectStartDate,
                    targetEndDate
            );
            if (calculateEstimatedDays(schedule.startDate(), schedule.endDate())
                    != schedule.estimatedDays()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid estimated days");
            }

            List<Long> predecessorIds = safeIds(schedule.predecessorWbsIds());
            predecessorsByWbsId.put(schedule.wbsId(), predecessorIds);
        }

        validatePredecessorIds(predecessorsByWbsId, scheduleWbsIds);
        validateNoScheduleCycle(predecessorsByWbsId);
        validateLegacyPredecessorDates(schedules, predecessorsByWbsId);
    }

    private void validateScheduleDateRange(
            Project project,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate projectStartDate,
            LocalDate targetEndDate
    ) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }
        if (startDate.isBefore(projectStartDate) || endDate.isAfter(targetEndDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
        if (project.getPlannedStartDate() != null
                && startDate.isBefore(project.getPlannedStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
        if (project.getPlannedEndDate() != null
                && endDate.isAfter(project.getPlannedEndDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
    }

    private void validateScheduleDateRangeAllowOverrun(
            Project project,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }
        if (project.getPlannedStartDate() != null
                && startDate.isBefore(project.getPlannedStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
    }

    private void validatePreparedPredecessors(
            List<PreparedSchedule> schedules,
            Map<Long, ProjectWbsTask> wbsTaskById
    ) {
        Map<Long, List<Long>> predecessorsByWbsId = new LinkedHashMap<>();
        Map<Long, PreparedSchedule> scheduleByWbsId = new LinkedHashMap<>();
        for (PreparedSchedule schedule : schedules) {
            Long wbsId = schedule.wbsTask().getId();
            scheduleByWbsId.put(wbsId, schedule);
            predecessorsByWbsId.put(wbsId, schedule.predecessorWbsIds());
        }

        validatePredecessorIds(predecessorsByWbsId, wbsTaskById.keySet());
        validateNoScheduleCycle(predecessorsByWbsId);

        for (PreparedSchedule schedule : schedules) {
            for (Long predecessorWbsId : schedule.predecessorWbsIds()) {
                PreparedSchedule predecessor = scheduleByWbsId.get(predecessorWbsId);
                validateScenarioPredecessorDate(
                        predecessor.expected(),
                        schedule.expected()
                );
                validateScenarioPredecessorDate(
                        predecessor.recommended(),
                        schedule.recommended()
                );
                validateScenarioPredecessorDate(
                        predecessor.conservative(),
                        schedule.conservative()
                );
            }
        }
    }

    private void validatePredecessorIds(
            Map<Long, List<Long>> predecessorsByWbsId,
            Set<Long> allowedWbsIds
    ) {
        for (Map.Entry<Long, List<Long>> entry : predecessorsByWbsId.entrySet()) {
            Set<Long> uniquePredecessors = new LinkedHashSet<>();
            for (Long predecessorWbsId : entry.getValue()) {
                if (predecessorWbsId == null || !allowedWbsIds.contains(predecessorWbsId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "predecessor wbs not found");
                }
                if (entry.getKey().equals(predecessorWbsId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "invalid predecessor wbs");
                }
                if (!uniquePredecessors.add(predecessorWbsId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate predecessor wbs");
                }
            }
        }
    }

    private void validateNoScheduleCycle(
            Map<Long, List<Long>> predecessorsByWbsId
    ) {
        Set<Long> visited = new LinkedHashSet<>();
        Set<Long> visiting = new LinkedHashSet<>();
        for (Long wbsId : predecessorsByWbsId.keySet()) {
            if (!visited.contains(wbsId)) {
                walkPredecessors(wbsId, predecessorsByWbsId, visited, visiting);
            }
        }
    }

    private void walkPredecessors(
            Long wbsId,
            Map<Long, List<Long>> predecessorsByWbsId,
            Set<Long> visited,
            Set<Long> visiting
    ) {
        Deque<Long> stack = new ArrayDeque<>();
        Deque<Integer> childIndexStack = new ArrayDeque<>();
        stack.push(wbsId);
        childIndexStack.push(0);

        while (!stack.isEmpty()) {
            Long current = stack.peek();
            if (visited.contains(current)) {
                stack.pop();
                childIndexStack.pop();
                continue;
            }
            visiting.add(current);

            List<Long> predecessors =
                    predecessorsByWbsId.getOrDefault(current, List.of());
            int childIndex = childIndexStack.pop();
            if (childIndex < predecessors.size()) {
                Long predecessor = predecessors.get(childIndex);
                childIndexStack.push(childIndex + 1);
                if (visiting.contains(predecessor)) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "schedule dependency cycle"
                    );
                }
                if (!visited.contains(predecessor)) {
                    stack.push(predecessor);
                    childIndexStack.push(0);
                }
                continue;
            }

            stack.pop();
            visiting.remove(current);
            visited.add(current);
        }
    }

    private void validateLegacyPredecessorDates(
            List<ScheduleResultRequest> schedules,
            Map<Long, List<Long>> predecessorsByWbsId
    ) {
        Map<Long, ScheduleResultRequest> scheduleByWbsId = new LinkedHashMap<>();
        for (ScheduleResultRequest schedule : schedules) {
            scheduleByWbsId.put(schedule.wbsId(), schedule);
        }
        for (ScheduleResultRequest schedule : schedules) {
            for (Long predecessorWbsId :
                    predecessorsByWbsId.getOrDefault(schedule.wbsId(), List.of())) {
                ScheduleResultRequest predecessor =
                        scheduleByWbsId.get(predecessorWbsId);
                if (predecessor.endDate().isAfter(schedule.startDate())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "schedule conflict");
                }
            }
        }
    }

    private void validateScenarioPredecessorDate(
            ScenarioRange predecessor,
            ScenarioRange successor
    ) {
        if (predecessor.endDate().isAfter(successor.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "schedule conflict");
        }
    }

    private List<ProjectScheduleResponse.ScheduleDetail> toScheduleDetails(
            List<ProjectSchedule> schedules
    ) {
        Set<Long> parentIds = schedules.stream()
                .map(ProjectSchedule::getWbsTask)
                .filter(task -> task.getParentTask() != null)
                .map(task -> task.getParentTask().getId())
                .collect(java.util.stream.Collectors.toSet());
        return schedules.stream()
                .map(schedule -> toScheduleDetail(schedule, parentIds))
                .toList();
    }

    private ProjectScheduleResponse.ScheduleDetail toScheduleDetail(
            ProjectSchedule schedule,
            Set<Long> parentIds
    ) {
        Map<ScheduleScenarioType, ProjectScheduleScenario> scenarioByType =
                new EnumMap<>(ScheduleScenarioType.class);
        for (ProjectScheduleScenario scenario : schedule.getScenarios()) {
            if (scenarioByType.put(scenario.getScenarioType(), scenario) != null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "duplicate schedule scenario"
                );
            }
        }

        ProjectScheduleResponse.ScheduleDateRange expected =
                toResponseRange(schedule, scenarioByType.get(ScheduleScenarioType.EXPECTED));
        ProjectScheduleResponse.ScheduleDateRange recommended =
                toResponseRange(schedule, scenarioByType.get(ScheduleScenarioType.RECOMMENDED));
        ProjectScheduleResponse.ScheduleDateRange conservative =
                toResponseRange(schedule, scenarioByType.get(ScheduleScenarioType.CONSERVATIVE));

        return new ProjectScheduleResponse.ScheduleDetail(
                schedule.getId(),
                schedule.getWbsTask().getId(),
                schedule.getWbsTask().getTaskCode(),
                schedule.getWbsTask().getTaskName(),
                schedule.getWbsTask().getDescription(),
                schedule.getWbsTask().getParentTask() == null
                        ? null
                        : schedule.getWbsTask().getParentTask().getId(),
                resolveItemType(schedule.getWbsTask(), parentIds),
                schedule.getWbsTask().getOrderIndex(),
                expected,
                recommended,
                conservative,
                schedule.getPredecessors().stream()
                        .map(predecessor -> predecessor.getWbsTask().getId())
                        .sorted()
                        .toList(),
                schedule.isMilestone(),
                schedule.getBufferDays(),
                schedule.isConfirmed()
        );
    }

    private ProjectScheduleResponse.ScheduleDateRange toResponseRange(
            ProjectSchedule schedule,
            ProjectScheduleScenario scenario
    ) {
        if (scenario == null) {
            return new ProjectScheduleResponse.ScheduleDateRange(
                    schedule.getStartDate(),
                    schedule.getEndDate(),
                    schedule.getEstimatedDays()
            );
        }
        return new ProjectScheduleResponse.ScheduleDateRange(
                scenario.getStartDate(),
                scenario.getEndDate(),
                scenario.getEstimatedDays()
        );
    }

    private int calculateEstimatedDays(LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid estimated days");
        }
        return (int) days;
    }

    private String writeWarnings(List<String> warnings) {
        try {
            return objectMapper.writeValueAsString(safeWarnings(warnings));
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to store schedule warnings",
                    exception
            );
        }
    }

    private List<String> readWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    warningsJson,
                    new TypeReference<List<String>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SCHEDULE_DATA_CORRUPTED",
                    "저장된 일정 경고를 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private List<String> safeWarnings(List<String> warnings) {
        if (warnings == null) {
            return List.of();
        }
        return warnings.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private List<String> withOverrunWarnings(
            List<String> warnings,
            List<PreparedSchedule> schedules,
            LocalDate targetEndDate
    ) {
        List<String> merged = new ArrayList<>(safeWarnings(warnings).stream()
                .filter(warning -> !warning.startsWith(
                        "AI recommended schedule exceeds the project target end date:"))
                .toList());
        if (targetEndDate != null) {
            boolean exceeded = schedules.stream().anyMatch(schedule ->
                    schedule.expected().endDate().isAfter(targetEndDate)
                            || schedule.recommended().endDate().isAfter(targetEndDate)
                            || schedule.conservative().endDate().isAfter(targetEndDate));
            if (exceeded) {
                merged.add("AI recommended schedule exceeds the project target end date: "
                        + targetEndDate);
            }
        }
        return merged.stream().distinct().toList();
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(ids);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException invalidAiResponse(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_SCHEDULE_RESPONSE",
                message
        );
    }

    private record ScenarioRange(
            ScheduleScenarioType type,
            LocalDate startDate,
            LocalDate endDate,
            int estimatedDays
    ) {
    }

    private record PreparedSchedule(
            String externalScheduleId,
            ProjectWbsTask wbsTask,
            ScenarioRange expected,
            ScenarioRange recommended,
            ScenarioRange conservative,
            List<Long> predecessorWbsIds,
            boolean milestone,
            int bufferDays
    ) {
    }
}
