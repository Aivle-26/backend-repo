package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.ProjectAgentClient;
import com.aivle26.aipm.Dto.project.AgentRequestResult;
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
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private final ProjectAgentClient projectAgentClient;

    // 프로젝트와 확정 요구사항 존재를 검증한 뒤 AI WBS 생성을 요청한다.
    @Transactional(readOnly = true)
    public AgentRequestResult requestWbsGeneration(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (!projectRequirementRepository.existsByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed requirement not found");
        }
        return projectAgentClient.requestWbsGeneration(projectId);
    }

    // AI WBS 요청을 검증해 결과와 계층형 작업을 프로젝트에 저장하고 저장 결과를 반환한다.
    @Transactional
    public SaveWbsResultResponse saveWbsResult(Long projectId, SaveWbsResultRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (!projectRequirementRepository.existsByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed requirement not found");
        }
        if (projectWbsResultRepository.existsByAgentExecutionId(request.agentExecutionId().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }
        if (projectWbsResultRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "wbs already exists");
        }

        List<ProjectRequirement> confirmedRequirements = projectRequirementRepository.findByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED);
        Map<Long, ProjectRequirement> requirementById = new LinkedHashMap<>();
        for (ProjectRequirement requirement : confirmedRequirements) {
            requirementById.put(requirement.getId(), requirement);
        }

        validateTasks(request.tasks(), requirementById);

        ProjectWbsResult wbsResult = new ProjectWbsResult();
        wbsResult.setProject(project);
        wbsResult.setAgentExecutionId(request.agentExecutionId().trim());
        wbsResult.setAgentVersion(request.agentVersion().trim());
        ProjectWbsResult savedWbsResult = projectWbsResultRepository.save(wbsResult);

        Map<String, ProjectWbsTask> taskByExternalId = new LinkedHashMap<>();
        for (WbsTaskResultRequest taskRequest : request.tasks()) {
            ProjectWbsTask task = new ProjectWbsTask();
            task.setProject(project);
            task.setWbsResult(savedWbsResult);
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
            task.setConfirmed(false);
            task.setRequirements(resolveRequirements(taskRequest.requirementIds(), requirementById));
            taskByExternalId.put(task.getExternalTaskId(), task);
        }

        for (ProjectWbsTask task : taskByExternalId.values()) {
            if (task.getParentExternalTaskId() != null) {
                task.setParentTask(taskByExternalId.get(task.getParentExternalTaskId()));
            }
        }

        projectWbsTaskRepository.saveAll(taskByExternalId.values());
        return new SaveWbsResultResponse(savedWbsResult.getId(), projectId, savedWbsResult.getAgentExecutionId(), taskByExternalId.size());
    }

    // WBS 작업의 필수값·외부 ID·부모·요구사항 참조가 유효한지 검증한다.
    private void validateTasks(List<WbsTaskResultRequest> tasks, Map<Long, ProjectRequirement> requirementById) {
        Map<String, String> parentByExternalId = new LinkedHashMap<>();
        Set<String> taskCodes = new LinkedHashSet<>();

        for (WbsTaskResultRequest task : tasks) {
            String externalTaskId = task.externalTaskId().trim();
            String parentExternalTaskId = normalizeNullable(task.parentExternalTaskId());
            String taskCode = task.taskCode().trim();

            if (parentByExternalId.putIfAbsent(externalTaskId, parentExternalTaskId) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task external id");
            }
            if (!taskCodes.add(taskCode)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task code");
            }
            if (task.estimatedHours() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid estimated hours");
            }
            if (parentExternalTaskId != null && externalTaskId.equals(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid parent task");
            }

            parseEnum(task.phase(), WbsPhase.class);
            parseEnum(task.difficulty(), WbsDifficulty.class);
            parseSkills(task.requiredSkills());
            resolveRequirements(task.requirementIds(), requirementById);
        }

        for (Map.Entry<String, String> entry : parentByExternalId.entrySet()) {
            String parentExternalTaskId = entry.getValue();
            if (parentExternalTaskId != null && !parentByExternalId.containsKey(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "parent task not found");
            }
        }

        validateNoCycle(parentByExternalId);
    }

    // 외부 작업 ID와 부모 관계를 순회해 WBS 계층에 순환이 없는지 검증한다.
    private void validateNoCycle(Map<String, String> parentByExternalId) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();

        for (String externalTaskId : parentByExternalId.keySet()) {
            if (!visited.contains(externalTaskId)) {
                walkParent(externalTaskId, parentByExternalId, visited, visiting);
            }
        }
    }

    // 한 WBS 작업의 부모 경로를 재귀 순회해 순환 참조를 탐지한다.
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

    // 작업의 요구사항 ID 목록을 프로젝트 요구사항 엔티티 집합으로 변환한다.
    private Set<ProjectRequirement> resolveRequirements(List<Long> requirementIds, Map<Long, ProjectRequirement> requirementById) {
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

    // AI 작업의 기술 문자열 목록을 중복 없는 WbsSkill 집합으로 변환한다.
    private Set<WbsSkill> parseSkills(List<String> values) {
        Set<WbsSkill> skills = new LinkedHashSet<>();
        for (String value : values) {
            skills.add(parseEnum(value, WbsSkill.class));
        }
        return skills;
    }

    // 문자열 값을 지정 WBS Enum으로 변환하고 허용되지 않은 값은 요청 오류로 처리한다.
    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
    }

    // 선택 문자열의 앞뒤 공백을 제거하고 빈 값은 null로 반환한다.
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
