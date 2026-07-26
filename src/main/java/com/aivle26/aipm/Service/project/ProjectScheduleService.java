package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.ProjectAgentClient;
import com.aivle26.aipm.Dto.project.AgentRequestResult;
import com.aivle26.aipm.Dto.project.SaveScheduleResultRequest;
import com.aivle26.aipm.Dto.project.SaveScheduleResultResponse;
import com.aivle26.aipm.Dto.project.ScheduleResultRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectScheduleResult;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectScheduleService {
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectScheduleResultRepository projectScheduleResultRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final ProjectAgentClient projectAgentClient;

    // 프로젝트와 저장 WBS 존재를 검증한 뒤 AI 일정 생성을 요청한다.
    @Transactional(readOnly = true)
    public AgentRequestResult requestScheduleGeneration(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (!projectWbsTaskRepository.existsByProjectIdAndConfirmedTrue(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        return projectAgentClient.requestScheduleGeneration(projectId);
    }

    // AI 일정 요청의 기간과 선후행 관계를 검증해 일정 결과를 저장하고 반환한다.
    @Transactional
    public SaveScheduleResultResponse saveScheduleResult(Long projectId, SaveScheduleResultRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (!projectWbsTaskRepository.existsByProjectIdAndConfirmedTrue(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        if (projectScheduleResultRepository.existsByAgentExecutionId(request.agentExecutionId().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }
        if (projectScheduleResultRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "schedule already exists");
        }
        validateProjectWindow(project, request.projectStartDate(), request.targetEndDate());

        List<ProjectWbsTask> confirmedWbsTasks = projectWbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId);
        Map<Long, ProjectWbsTask> wbsTaskById = new LinkedHashMap<>();
        for (ProjectWbsTask task : confirmedWbsTasks) {
            wbsTaskById.put(task.getId(), task);
        }

        validateSchedules(project, request.schedules(), wbsTaskById, request.projectStartDate(), request.targetEndDate());

        ProjectScheduleResult scheduleResult = new ProjectScheduleResult();
        scheduleResult.setProject(project);
        scheduleResult.setAgentExecutionId(request.agentExecutionId().trim());
        scheduleResult.setAgentVersion(request.agentVersion().trim());
        scheduleResult.setProjectStartDate(request.projectStartDate());
        scheduleResult.setTargetEndDate(request.targetEndDate());
        ProjectScheduleResult savedResult = projectScheduleResultRepository.save(scheduleResult);

        Map<Long, ProjectSchedule> scheduleByWbsId = new LinkedHashMap<>();
        for (ScheduleResultRequest scheduleRequest : request.schedules()) {
            ProjectSchedule schedule = new ProjectSchedule();
            schedule.setProject(project);
            schedule.setScheduleResult(savedResult);
            schedule.setWbsTask(wbsTaskById.get(scheduleRequest.wbsId()));
            schedule.setExternalScheduleId(scheduleRequest.externalScheduleId().trim());
            schedule.setStartDate(scheduleRequest.startDate());
            schedule.setEndDate(scheduleRequest.endDate());
            schedule.setEstimatedDays(scheduleRequest.estimatedDays());
            schedule.setMilestone(scheduleRequest.milestone());
            schedule.setBufferDays(scheduleRequest.bufferDays());
            schedule.setConfirmed(false);
            scheduleByWbsId.put(scheduleRequest.wbsId(), schedule);
        }

        for (ScheduleResultRequest scheduleRequest : request.schedules()) {
            ProjectSchedule schedule = scheduleByWbsId.get(scheduleRequest.wbsId());
            Set<ProjectSchedule> predecessors = new LinkedHashSet<>();
            for (Long predecessorWbsId : scheduleRequest.predecessorWbsIds()) {
                predecessors.add(scheduleByWbsId.get(predecessorWbsId));
            }
            schedule.setPredecessors(predecessors);
        }

        projectScheduleRepository.saveAll(scheduleByWbsId.values());
        return new SaveScheduleResultResponse(savedResult.getId(), projectId, savedResult.getAgentExecutionId(), scheduleByWbsId.size());
    }

    // 요청 일정 기간이 유효하고 프로젝트 계획 기간 안에 포함되는지 검증한다.
    private void validateProjectWindow(Project project, LocalDate projectStartDate, LocalDate targetEndDate) {
        if (targetEndDate.isBefore(projectStartDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }
        if (project.getPlannedStartDate() != null && projectStartDate.isBefore(project.getPlannedStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
        if (project.getPlannedEndDate() != null && targetEndDate.isAfter(project.getPlannedEndDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
        }
    }

    // 작업별 일정의 필수값·중복·WBS 참조·기간·선행 관계를 종합 검증한다.
    private void validateSchedules(
            Project project,
            List<ScheduleResultRequest> schedules,
            Map<Long, ProjectWbsTask> wbsTaskById,
            LocalDate projectStartDate,
            LocalDate targetEndDate
    ) {
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
            if (schedule.endDate().isBefore(schedule.startDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
            }
            long expectedDays = ChronoUnit.DAYS.between(schedule.startDate(), schedule.endDate()) + 1;
            if (expectedDays != schedule.estimatedDays()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid estimated days");
            }
            if (schedule.startDate().isBefore(projectStartDate) || schedule.endDate().isAfter(targetEndDate)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
            }
            if (project.getPlannedStartDate() != null && schedule.startDate().isBefore(project.getPlannedStartDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
            }
            if (project.getPlannedEndDate() != null && schedule.endDate().isAfter(project.getPlannedEndDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "project date exceeded");
            }

            List<Long> predecessorIds = new ArrayList<>();
            for (Long predecessorWbsId : schedule.predecessorWbsIds()) {
                if (!wbsTaskById.containsKey(predecessorWbsId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "predecessor wbs not found");
                }
                if (schedule.wbsId().equals(predecessorWbsId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "invalid predecessor wbs");
                }
                predecessorIds.add(predecessorWbsId);
            }
            predecessorsByWbsId.put(schedule.wbsId(), predecessorIds);
        }

        for (Map.Entry<Long, List<Long>> entry : predecessorsByWbsId.entrySet()) {
            for (Long predecessorWbsId : entry.getValue()) {
                if (!scheduleWbsIds.contains(predecessorWbsId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "predecessor wbs not found");
                }
            }
        }

        validateNoScheduleCycle(predecessorsByWbsId);
        validatePredecessorDates(schedules, predecessorsByWbsId);
    }

    // WBS별 선행 작업 관계를 순회해 일정 의존성에 순환이 없는지 검증한다.
    private void validateNoScheduleCycle(Map<Long, List<Long>> predecessorsByWbsId) {
        Set<Long> visited = new LinkedHashSet<>();
        Set<Long> visiting = new LinkedHashSet<>();

        for (Long wbsId : predecessorsByWbsId.keySet()) {
            if (!visited.contains(wbsId)) {
                walkPredecessors(wbsId, predecessorsByWbsId, visited, visiting);
            }
        }
    }

    // 한 WBS 작업의 선행 관계를 재귀 순회해 순환 의존성을 탐지한다.
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
            if (!visiting.contains(current)) {
                visiting.add(current);
            }

            List<Long> predecessors = predecessorsByWbsId.getOrDefault(current, List.of());
            int childIndex = childIndexStack.pop();
            if (childIndex < predecessors.size()) {
                Long predecessor = predecessors.get(childIndex);
                childIndexStack.push(childIndex + 1);
                if (visiting.contains(predecessor)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "schedule dependency cycle");
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

    // 각 작업 시작일이 모든 선행 작업 종료일 이후인지 검증한다.
    private void validatePredecessorDates(List<ScheduleResultRequest> schedules, Map<Long, List<Long>> predecessorsByWbsId) {
        Map<Long, ScheduleResultRequest> scheduleByWbsId = new LinkedHashMap<>();
        for (ScheduleResultRequest schedule : schedules) {
            scheduleByWbsId.put(schedule.wbsId(), schedule);
        }

        for (ScheduleResultRequest schedule : schedules) {
            for (Long predecessorWbsId : predecessorsByWbsId.getOrDefault(schedule.wbsId(), List.of())) {
                ScheduleResultRequest predecessor = scheduleByWbsId.get(predecessorWbsId);
                if (predecessor.endDate().isAfter(schedule.startDate())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "schedule conflict");
                }
            }
        }
    }
}
