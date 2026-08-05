package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiImpactAnalysisRequest;
import com.aivle26.aipm.Dto.AiImpactAnalysisResponse;
import com.aivle26.aipm.Dto.ImpactAnalysisRequest;
import com.aivle26.aipm.Dto.ImpactAnalysisResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프로젝트 조정 여부 평가(요구사항 변경 영향도).
 *
 * <p>useLlm=true("AI 분석")이면 백엔드가 확정 WBS·담당자·프로젝트 종료일을 모아
 * AI 서버로 넘겨 영향 업무 수·추가 작업일 등을 자동 산출하게 한다.
 * useLlm=false("평가하기")이면 프론트가 넘긴 수동 수치로 규칙 계산만 수행한다.
 * (AI 서버는 무상태 계산기라 결과를 저장하지 않고 온디맨드로 계산한다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactAnalysisService {

    /** ai-server ImpactWBSTask.estimated_days 환산 기준(1일 = 8시간). */
    private static final double HOURS_PER_DAY = 8.0;

    private final ProjectRepository projectRepository;
    private final ImpactAnalysisAgentClient agentClient;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectTaskAssignmentRepository taskAssignmentRepository;
    private final ProjectScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public ImpactAnalysisResponse assess(Long projectId, ImpactAnalysisRequest request) {
        authorizationService.requireProjectPm(projectId);
        Project project = findProject(projectId);

        boolean useLlm = request.useLlmOrDefault();

        List<AiImpactAnalysisRequest.WbsTask> wbsTasks = List.of();
        String projectEndDate = null;
        if (useLlm) {
            wbsTasks = buildWbsContext(projectId);
            projectEndDate = resolveProjectEndDate(projectId);
        }

        AiImpactAnalysisRequest aiRequest = new AiImpactAnalysisRequest(
                project.getId(),
                request.requirementId(),
                request.changeTitle(),
                request.changeDescription(),
                request.affectedTaskCount(),
                request.affectedMemberCount(),
                request.remainingDays(),
                request.additionalWorkDays(),
                request.scopeChanged(),
                request.databaseChanged(),
                request.apiChanged(),
                request.uiChanged(),
                useLlm,
                wbsTasks,
                projectEndDate);

        AiImpactAnalysisResponse aiResponse = agentClient.assess(aiRequest);
        return ImpactAnalysisResponse.from(aiResponse);
    }

    /** 확정 WBS 작업 + 담당자/상태를 AI 컨텍스트로 조립한다. */
    private List<AiImpactAnalysisRequest.WbsTask> buildWbsContext(Long projectId) {
        List<ProjectWbsTask> tasks = wbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId);
        if (tasks.isEmpty()) {
            return List.of();
        }

        Map<Long, ProjectTaskAssignment> assignmentByTaskId =
                taskAssignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                        .stream()
                        .filter(assignment -> assignment.getWbsTask() != null)
                        .collect(Collectors.toMap(
                                assignment -> assignment.getWbsTask().getId(),
                                Function.identity(),
                                (first, second) -> first));

        return tasks.stream()
                .map(task -> {
                    ProjectTaskAssignment assignment = assignmentByTaskId.get(task.getId());
                    String assignee = assignment != null ? assignment.getEmployeeNumber() : null;
                    String status = assignment != null && assignment.getStatus() != null
                            ? assignment.getStatus().name()
                            : "TODO";
                    return new AiImpactAnalysisRequest.WbsTask(
                            task.getId(),
                            task.getTaskName(),
                            task.getDescription(),
                            assignee,
                            status,
                            toEstimatedDays(task.getEstimatedHours()));
                })
                .toList();
    }

    /** 프로젝트 종료일 = 저장된 일정 중 가장 늦은 종료일. 없으면 null. */
    private String resolveProjectEndDate(Long projectId) {
        return scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .map(ProjectSchedule::getEndDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .map(LocalDate::toString)
                .orElse(null);
    }

    private int toEstimatedDays(int estimatedHours) {
        if (estimatedHours <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round(estimatedHours / HOURS_PER_DAY));
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }
}
