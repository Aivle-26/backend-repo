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

    // DB의 확정 요구사항만 AI Server에 전달하고 반환된 WBS를 원본과 편집본으로 저장한다.
    @Transactional
    public ProjectWbsResponse generateWbs(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getDraftProject(projectId);
        if (projectWbsResultRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "wbs already exists");
        }

        List<ProjectRequirement> requirements = getConfirmedRequirements(projectId);
        SaveWbsResultRequest aiResult = planningWbsClient.generateWbs(toGenerationRequest(requirements));
        ProjectWbsResult savedResult = saveNewWbsResult(project, aiResult, toRequirementMap(requirements));
        return toResponse(savedResult);
    }

    // AI Server가 전달한 WBS 결과를 검증해 최초 제안 스냅샷과 편집용 작업으로 저장한다.
    @Transactional
    public SaveWbsResultResponse saveWbsResult(Long projectId, SaveWbsResultRequest request) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getDraftProject(projectId);
        if (projectWbsResultRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "wbs already exists");
        }

        ProjectWbsResult savedResult = saveNewWbsResult(
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
        validateTasks(request.tasks(), requirementById);
        deleteFinalTasks(projectId);
        saveTasks(project, result, request.tasks(), requirementById, true);
        projectWbsTaskRepository.flush();
        return toResponse(result);
    }

    private ProjectWbsResult saveNewWbsResult(
            Project project,
            SaveWbsResultRequest request,
            Map<Long, ProjectRequirement> requirementById
    ) {
        String agentExecutionId = requireText(request == null ? null : request.agentExecutionId(), "agentExecutionId");
        String agentVersion = requireText(request.agentVersion(), "agentVersion");
        if (projectWbsResultRepository.existsByAgentExecutionId(agentExecutionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }

        validateTasks(request.tasks(), requirementById);

        ProjectWbsResult result = new ProjectWbsResult();
        result.setProject(project);
        result.setAgentExecutionId(agentExecutionId);
        result.setAgentVersion(agentVersion);
        result.setInitialTasksJson(writeInitialTasks(request.tasks()));
        ProjectWbsResult savedResult = projectWbsResultRepository.save(result);

        saveTasks(project, savedResult, request.tasks(), requirementById, false);
        projectWbsTaskRepository.flush();
        return savedResult;
    }

    private void saveTasks(
            Project project,
            ProjectWbsResult result,
            List<WbsTaskResultRequest> taskRequests,
            Map<Long, ProjectRequirement> requirementById,
            boolean confirmed
    ) {
        Map<String, ProjectWbsTask> taskByExternalId = new LinkedHashMap<>();
        for (WbsTaskResultRequest taskRequest : taskRequests) {
            ProjectWbsTask task = new ProjectWbsTask();
            task.setProject(project);
            task.setWbsResult(result);
            task.setExternalTaskId(taskRequest.externalTaskId().trim());
            task.setParentExternalTaskId(normalizeNullable(taskRequest.parentExternalTaskId()));
            task.setTaskCode(taskRequest.taskCode().trim());
            task.setTaskName(taskRequest.taskName().trim());
            task.setDescription(taskRequest.description().trim());
            task.setPhase(parseEnum(taskRequest.phase(), WbsPhase.class));
            task.setRequiredSkills(parseSkills(taskRequest.requiredSkills()));
            task.setDifficulty(parseEnum(taskRequest.difficulty(), WbsDifficulty.class));
            task.setEstimatedHours(taskRequest.estimatedHours());
            task.setOrderIndex(taskRequest.orderIndex());
            task.setConfirmed(confirmed);
            task.setRequirements(resolveRequirements(taskRequest.requirementIds(), requirementById));
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
    private void validateTasks(List<WbsTaskResultRequest> tasks, Map<Long, ProjectRequirement> requirementById) {
        if (tasks == null || tasks.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "wbs task not found");
        }

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
            requireText(task.taskName(), "taskName");
            requireText(task.description(), "description");

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

            parseEnum(task.phase(), WbsPhase.class);
            parseEnum(task.difficulty(), WbsDifficulty.class);
            parseSkills(task.requiredSkills());
            resolveRequirements(task.requirementIds(), requirementById);
        }

        for (String parentExternalTaskId : parentByExternalId.values()) {
            if (parentExternalTaskId != null && !parentByExternalId.containsKey(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "parent task not found");
            }
        }
        validateNoCycle(parentByExternalId);
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
}
