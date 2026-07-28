package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.ProjectWbsResponse;
import com.aivle26.aipm.Dto.project.SaveFinalWbsRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultResponse;
import com.aivle26.aipm.Dto.project.WbsTaskResultRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsResult;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.WbsDifficulty;
import com.aivle26.aipm.Entity.project.WbsPhase;
import com.aivle26.aipm.Entity.project.WbsSkill;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.PlanningWbsClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectWbsService {
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectWbsResultRepository projectWbsResultRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final PlanningWbsClient planningWbsClient;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ObjectMapper objectMapper;

    // 확정 요구사항으로 WBS를 생성한다. 재생성 시 AI 제안만 교체하고 사용자의 최종 WBS는 유지한다.
    @Transactional
    public ProjectWbsResponse generateWbs(Long projectId) {
        Project project = requireAuthorizedDraftProject(projectId);
        List<ProjectRequirement> requirements = getConfirmedRequirements(projectId);
        SaveWbsResultRequest aiResult = planningWbsClient.generateWbs(toGenerationRequest(requirements));
        ProjectWbsResult savedResult = saveWbsSuggestion(project, aiResult, toRequirementMap(requirements));
        return toResponse(savedResult);
    }

    // AI Server가 전달한 WBS만 저장한다. 기존 결과가 있으면 AI 제안만 교체하고 최종 WBS는 보존한다.
    @Transactional
    public SaveWbsResultResponse saveWbsResult(Long projectId, SaveWbsResultRequest request) {
        Project project = requireAuthorizedDraftProject(projectId);

        ProjectWbsResult savedResult = saveWbsSuggestion(
                project,
                request,
                toRequirementMap(getConfirmedRequirements(projectId))
        );
        return new SaveWbsResultResponse(
                savedResult.getId(),
                projectId,
                savedResult.getAgentExecutionId(),
                request.tasks().size()
        );
    }

    // 프로젝트별 AI 최초안과 현재 최종안을 한 번에 조회해 좌우 편집 화면을 복원한다.
    @Transactional(readOnly = true)
    public ProjectWbsResponse getWbs(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        getProject(projectId);
        ProjectWbsResult result = projectWbsResultRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wbs not found"));
        return toResponse(result);
    }

    // 사용자가 편집한 전체 목록을 원자적으로 교체 저장해 추가·수정·삭제·순서 변경을 반영한다.
    @Transactional
    public ProjectWbsResponse saveFinalWbs(Long projectId, SaveFinalWbsRequest request) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getDraftProject(projectId);
        ProjectWbsResult result = projectWbsResultRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wbs not found"));

        if (projectScheduleRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "schedule already exists");
        }

        Map<Long, ProjectRequirement> requirementById = toRequirementMap(getConfirmedRequirements(projectId));
        List<PreparedWbsTask> preparedTasks = prepareTasks(request.tasks(), requirementById);
        deleteFinalTasks(projectId);
        saveTasks(project, result, preparedTasks, true);
        projectWbsTaskRepository.flush();
        return toResponse(result);
    }

    private ProjectWbsResult saveWbsSuggestion(
            Project project,
            SaveWbsResultRequest request,
            Map<Long, ProjectRequirement> requirementById
    ) {
        String agentExecutionId = requireText(request == null ? null : request.agentExecutionId(), "agentExecutionId");
        String agentVersion = requireText(request.agentVersion(), "agentVersion");
        if (projectWbsResultRepository.existsByAgentExecutionId(agentExecutionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }

        List<PreparedWbsTask> preparedTasks = prepareTasks(request.tasks(), requirementById);

        ProjectWbsResult existingResult = projectWbsResultRepository.findByProjectId(project.getId())
                .orElse(null);
        if (existingResult != null) {
            // 재생성 결과는 왼쪽 AI 제안에만 반영한다. 오른쪽 최종 WBS 작업 행은 수정하지 않는다.
            existingResult.setAgentExecutionId(agentExecutionId);
            existingResult.setAgentVersion(agentVersion);
            existingResult.setInitialTasksJson(writeInitialTasks(request.tasks()));
            return projectWbsResultRepository.save(existingResult);
        }

        // 최초 생성에서만 AI 제안을 오른쪽 편집용 WBS의 초깃값으로 함께 저장한다.
        ProjectWbsResult result = new ProjectWbsResult();
        result.setProject(project);
        result.setAgentExecutionId(agentExecutionId);
        result.setAgentVersion(agentVersion);
        result.setInitialTasksJson(writeInitialTasks(request.tasks()));
        ProjectWbsResult savedResult = projectWbsResultRepository.save(result);

        saveTasks(project, savedResult, preparedTasks, false);
        projectWbsTaskRepository.flush();
        return savedResult;
    }

    private void saveTasks(
            Project project,
            ProjectWbsResult result,
            List<PreparedWbsTask> preparedTasks,
            boolean confirmed
    ) {
        Map<String, ProjectWbsTask> taskByExternalId = new LinkedHashMap<>();
        for (PreparedWbsTask preparedTask : preparedTasks) {
            ProjectWbsTask task = new ProjectWbsTask();
            task.setProject(project);
            task.setWbsResult(result);
            task.setExternalTaskId(preparedTask.externalTaskId());
            task.setParentExternalTaskId(preparedTask.parentExternalTaskId());
            task.setTaskCode(preparedTask.taskCode());
            task.setTaskName(preparedTask.taskName());
            task.setDescription(preparedTask.description());
            task.setPhase(preparedTask.phase());
            task.setRequiredSkills(new LinkedHashSet<>(preparedTask.requiredSkills()));
            task.setDifficulty(preparedTask.difficulty());
            task.setEstimatedHours(preparedTask.estimatedHours());
            task.setOrderIndex(preparedTask.orderIndex());
            task.setConfirmed(confirmed);
            task.setRequirements(new LinkedHashSet<>(preparedTask.requirements()));
            taskByExternalId.put(task.getExternalTaskId(), task);
        }

        projectWbsTaskRepository.saveAll(taskByExternalId.values());
        projectWbsTaskRepository.flush();

        List<ProjectWbsTask> childTasks = new ArrayList<>();
        for (ProjectWbsTask task : taskByExternalId.values()) {
            if (task.getParentExternalTaskId() != null) {
                task.setParentTask(taskByExternalId.get(task.getParentExternalTaskId()));
                childTasks.add(task);
            }
        }
        if (!childTasks.isEmpty()) {
            projectWbsTaskRepository.saveAll(childTasks);
        }
    }

    private ProjectWbsResponse toResponse(ProjectWbsResult result) {
        List<ProjectWbsTask> finalTasks = projectWbsTaskRepository
                .findByProjectIdOrderByOrderIndexAscIdAsc(result.getProject().getId());
        List<WbsTaskResultRequest> initialTasks = readInitialTasks(result.getInitialTasksJson(), finalTasks);

        return new ProjectWbsResponse(
                result.getId(),
                result.getProject().getId(),
                result.getAgentExecutionId(),
                result.getAgentVersion(),
                !finalTasks.isEmpty() && finalTasks.stream().allMatch(ProjectWbsTask::isConfirmed),
                result.getCreatedAt(),
                initialTasks.stream()
                        .sorted(Comparator.comparingInt(WbsTaskResultRequest::orderIndex))
                        .map(this::toInitialTaskDetail)
                        .toList(),
                finalTasks.stream().map(this::toFinalTaskDetail).toList()
        );
    }

    private ProjectWbsResponse.WbsTaskDetail toInitialTaskDetail(WbsTaskResultRequest task) {
        return new ProjectWbsResponse.WbsTaskDetail(
                null,
                task.externalTaskId().trim(),
                normalizeNullable(task.parentExternalTaskId()),
                task.taskCode().trim(),
                task.taskName().trim(),
                task.description().trim(),
                parseEnum(task.phase(), WbsPhase.class),
                parseSkills(task.requiredSkills()),
                parseEnum(task.difficulty(), WbsDifficulty.class),
                task.estimatedHours(),
                task.orderIndex(),
                List.copyOf(task.requirementIds()),
                false
        );
    }

    private ProjectWbsResponse.WbsTaskDetail toFinalTaskDetail(ProjectWbsTask task) {
        List<Long> requirementIds = task.getRequirements().stream()
                .map(ProjectRequirement::getId)
                .sorted()
                .toList();
        return new ProjectWbsResponse.WbsTaskDetail(
                task.getId(),
                task.getExternalTaskId(),
                task.getParentExternalTaskId(),
                task.getTaskCode(),
                task.getTaskName(),
                task.getDescription(),
                task.getPhase(),
                new LinkedHashSet<>(task.getRequiredSkills()),
                task.getDifficulty(),
                task.getEstimatedHours(),
                task.getOrderIndex(),
                requirementIds,
                task.isConfirmed()
        );
    }

    private PlanningWbsGenerationRequest toGenerationRequest(List<ProjectRequirement> requirements) {
        return new PlanningWbsGenerationRequest(requirements.stream()
                .sorted(Comparator.comparing(ProjectRequirement::getId))
                .map(requirement -> new PlanningWbsGenerationRequest.RequirementData(
                        requirement.getId(),
                        requirement.getType().name(),
                        requirement.getTitle(),
                        requirement.getDescription(),
                        requirement.getAcceptanceCriteria(),
                        requirement.getDueDate(),
                        requirement.getDeliverableName(),
                        requirement.getSecurityCondition(),
                        requirement.getPriority().name()
                ))
                .toList());
    }

    private void deleteFinalTasks(Long projectId) {
        projectWbsTaskRepository.clearParentTasksByProjectId(projectId);
        projectWbsTaskRepository.deleteRequirementLinksByProjectId(projectId);
        projectWbsTaskRepository.deleteSkillLinksByProjectId(projectId);
        projectWbsTaskRepository.deleteAllByProjectId(projectId);
        projectWbsTaskRepository.flush();
    }

    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
    }

    private Project getDraftProject(Long projectId) {
        Project project = getProject(projectId);
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        return project;
    }

    private Project requireAuthorizedDraftProject(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        return getDraftProject(projectId);
    }

    private List<ProjectRequirement> getConfirmedRequirements(Long projectId) {
        List<ProjectRequirement> requirements = projectRequirementRepository
                .findByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED);
        if (requirements.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed requirement not found");
        }
        return requirements;
    }

    private Map<Long, ProjectRequirement> toRequirementMap(List<ProjectRequirement> requirements) {
        Map<Long, ProjectRequirement> requirementById = new LinkedHashMap<>();
        for (ProjectRequirement requirement : requirements) {
            requirementById.put(requirement.getId(), requirement);
        }
        return requirementById;
    }

    private String writeInitialTasks(List<WbsTaskResultRequest> tasks) {
        try {
            return objectMapper.writeValueAsString(tasks);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store wbs", exception);
        }
    }

    private List<WbsTaskResultRequest> readInitialTasks(
            String initialTasksJson,
            List<ProjectWbsTask> fallbackTasks
    ) {
        if (initialTasksJson == null || initialTasksJson.isBlank()) {
            return fallbackTasks.stream().map(this::toTaskRequest).toList();
        }
        try {
            return objectMapper.readValue(
                    initialTasksJson,
                    new TypeReference<List<WbsTaskResultRequest>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "WBS_DATA_CORRUPTED",
                    "저장된 AI WBS 원본을 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private WbsTaskResultRequest toTaskRequest(ProjectWbsTask task) {
        return new WbsTaskResultRequest(
                task.getExternalTaskId(),
                task.getParentExternalTaskId(),
                task.getTaskCode(),
                task.getTaskName(),
                task.getDescription(),
                task.getPhase().name(),
                task.getRequiredSkills().stream().map(Enum::name).toList(),
                task.getDifficulty().name(),
                task.getEstimatedHours(),
                task.getOrderIndex(),
                task.getRequirements().stream().map(ProjectRequirement::getId).sorted().toList()
        );
    }

    // WBS 작업의 필수값·외부 ID·부모·순서·요구사항 참조가 유효한지 검증한다.
    private List<PreparedWbsTask> prepareTasks(List<WbsTaskResultRequest> tasks, Map<Long, ProjectRequirement> requirementById) {
        if (tasks == null || tasks.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "wbs task not found");
        }

        List<PreparedWbsTask> preparedTasks = new ArrayList<>();
        Map<String, String> parentByExternalId = new LinkedHashMap<>();
        Set<String> taskCodes = new LinkedHashSet<>();
        Set<Integer> orderIndexes = new LinkedHashSet<>();

        for (WbsTaskResultRequest task : tasks) {
            if (task == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid wbs task");
            }
            String externalTaskId = requireText(task.externalTaskId(), "externalTaskId");
            String parentExternalTaskId = normalizeNullable(task.parentExternalTaskId());
            String taskCode = requireText(task.taskCode(), "taskCode");
            String taskName = requireText(task.taskName(), "taskName");
            String description = requireText(task.description(), "description");

            if (parentByExternalId.putIfAbsent(externalTaskId, parentExternalTaskId) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task external id");
            }
            if (!taskCodes.add(taskCode)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task code");
            }
            if (!orderIndexes.add(task.orderIndex())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task order");
            }
            if (task.estimatedHours() <= 0 || task.orderIndex() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid wbs task");
            }
            if (parentExternalTaskId != null && externalTaskId.equals(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid parent task");
            }

            preparedTasks.add(new PreparedWbsTask(
                    externalTaskId,
                    parentExternalTaskId,
                    taskCode,
                    taskName,
                    description,
                    parseEnum(task.phase(), WbsPhase.class),
                    parseSkills(task.requiredSkills()),
                    parseEnum(task.difficulty(), WbsDifficulty.class),
                    task.estimatedHours(),
                    task.orderIndex(),
                    resolveRequirements(task.requirementIds(), requirementById)
            ));
        }

        for (String parentExternalTaskId : parentByExternalId.values()) {
            if (parentExternalTaskId != null && !parentByExternalId.containsKey(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "parent task not found");
            }
        }
        validateNoCycle(parentByExternalId);
        return preparedTasks;
    }

    private void validateNoCycle(Map<String, String> parentByExternalId) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String externalTaskId : parentByExternalId.keySet()) {
            if (!visited.contains(externalTaskId)) {
                walkParent(externalTaskId, parentByExternalId, visited, visiting);
            }
        }
    }

    private void walkParent(
            String externalTaskId,
            Map<String, String> parentByExternalId,
            Set<String> visited,
            Set<String> visiting
    ) {
        Deque<String> stack = new ArrayDeque<>();
        String current = externalTaskId;
        while (current != null && !visited.contains(current)) {
            if (!visiting.add(current)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "wbs cycle reference");
            }
            stack.push(current);
            current = parentByExternalId.get(current);
        }
        while (!stack.isEmpty()) {
            String node = stack.pop();
            visiting.remove(node);
            visited.add(node);
        }
    }

    private Set<ProjectRequirement> resolveRequirements(
            List<Long> requirementIds,
            Map<Long, ProjectRequirement> requirementById
    ) {
        if (requirementIds == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid requirement id");
        }
        Set<ProjectRequirement> requirements = new LinkedHashSet<>();
        for (Long requirementId : requirementIds) {
            ProjectRequirement requirement = requirementById.get(requirementId);
            if (requirement == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid requirement id");
            }
            requirements.add(requirement);
        }
        return requirements;
    }

    private Set<WbsSkill> parseSkills(List<String> values) {
        if (values == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
        Set<WbsSkill> skills = new LinkedHashSet<>();
        for (String value : values) {
            skills.add(parseEnum(value, WbsSkill.class));
        }
        return skills;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PreparedWbsTask(
            String externalTaskId,
            String parentExternalTaskId,
            String taskCode,
            String taskName,
            String description,
            WbsPhase phase,
            Set<WbsSkill> requiredSkills,
            WbsDifficulty difficulty,
            int estimatedHours,
            int orderIndex,
            Set<ProjectRequirement> requirements
    ) {
    }
}
