package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.ProjectDetailResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectLifecycleService {
    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final ProjectCostEstimateRepository costEstimateRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProjectDetailResponse getDetail(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        Project project = projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> projectNotFound(projectId));
        return toResponse(project, readiness(project));
    }

    @Transactional
    public ProjectDetailResponse finalizeProject(Long projectId) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> projectNotFound(projectId));
        authorizationService.requireProjectPm(project);

        if (project.getStatus() == ProjectStatus.ACTIVE) {
            return toResponse(project, readiness(project));
        }
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROJECT_FINALIZATION_NOT_ALLOWED",
                    "Only a draft project can be finalized."
            );
        }

        ProjectDetailResponse.FinalizationReadiness readiness = readiness(project);
        if (!readiness.ready()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROJECT_NOT_READY",
                    "Project is not ready to finalize. missingItems=" + readiness.missingItems()
            );
        }

        project.setStatus(ProjectStatus.ACTIVE);
        Project saved = projectRepository.saveAndFlush(project);
        return toResponse(saved, readiness);
    }

    private ProjectDetailResponse.FinalizationReadiness readiness(Project project) {
        Long projectId = project.getId();
        boolean projectInfoReady = hasCompleteProjectInfo(project);
        List<ProjectRequirement> finalRequirements = requirementRepository
                .findByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED);
        boolean requirementsReady = !finalRequirements.isEmpty();

        List<ProjectWbsTask> confirmedTasks = wbsTaskRepository
                .findByProjectIdAndConfirmedTrue(projectId);
        boolean wbsReady = !confirmedTasks.isEmpty();
        Set<Long> confirmedWbsIds = idsOf(confirmedTasks);
        Set<Long> leafWbsIds = leafIdsOf(confirmedTasks);

        List<ProjectSchedule> schedules = scheduleRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId);
        Set<Long> scheduledWbsIds = schedules.stream()
                .map(schedule -> schedule.getWbsTask().getId())
                .collect(Collectors.toSet());
        boolean scheduleReady = wbsReady
                && scheduledWbsIds.containsAll(confirmedWbsIds)
                && schedules.stream().allMatch(ProjectSchedule::isConfirmed);

        Set<String> activeMemberNumbers = memberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId)
                .stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(Collectors.toSet());
        boolean teamMembersReady = !activeMemberNumbers.isEmpty();
        List<ProjectTaskAssignment> assignments = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId);
        Set<Long> assignedWbsIds = assignments.stream()
                .map(assignment -> assignment.getWbsTask().getId())
                .collect(Collectors.toSet());
        boolean assignmentsReady = !leafWbsIds.isEmpty()
                && assignedWbsIds.containsAll(leafWbsIds)
                && assignments.stream().allMatch(
                        assignment -> activeMemberNumbers.contains(assignment.getEmployeeNumber())
                );
        boolean costEstimateReady = costEstimateRepository.findByProjectId(projectId)
                .filter(cost -> cost.isConfirmed())
                .isPresent();

        List<String> missingItems = new ArrayList<>();
        addMissing(missingItems, projectInfoReady, "PROJECT_INFO");
        addMissing(missingItems, requirementsReady, "REQUIREMENTS");
        addMissing(missingItems, wbsReady, "WBS");
        addMissing(missingItems, scheduleReady, "SCHEDULE");
        addMissing(missingItems, teamMembersReady, "TEAM_MEMBERS");
        addMissing(missingItems, assignmentsReady, "ASSIGNMENTS");
        addMissing(missingItems, costEstimateReady, "COST_ESTIMATE");

        return new ProjectDetailResponse.FinalizationReadiness(
                missingItems.isEmpty(),
                projectInfoReady,
                requirementsReady,
                wbsReady,
                scheduleReady,
                teamMembersReady,
                assignmentsReady,
                costEstimateReady,
                List.copyOf(missingItems)
        );
    }

    private boolean hasCompleteProjectInfo(Project project) {
        return project.getName() != null
                && !project.getName().isBlank()
                && project.getDescription() != null
                && !project.getDescription().isBlank()
                && project.getPlannedStartDate() != null
                && project.getPlannedEndDate() != null
                && !project.getPlannedEndDate().isBefore(project.getPlannedStartDate());
    }

    private Set<Long> idsOf(List<ProjectWbsTask> tasks) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ProjectWbsTask task : tasks) {
            ids.add(task.getId());
        }
        return ids;
    }

    private Set<Long> leafIdsOf(List<ProjectWbsTask> tasks) {
        Set<Long> parentIds = new LinkedHashSet<>();
        for (ProjectWbsTask task : tasks) {
            if (task.getParentTask() != null) {
                parentIds.add(task.getParentTask().getId());
            }
        }
        Set<Long> leafIds = idsOf(tasks);
        leafIds.removeAll(parentIds);
        return leafIds;
    }

    private void addMissing(List<String> missingItems, boolean ready, String item) {
        if (!ready) {
            missingItems.add(item);
        }
    }

    private ProjectDetailResponse toResponse(
            Project project,
            ProjectDetailResponse.FinalizationReadiness readiness
    ) {
        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getClientOrganization(),
                project.getPm().getEmployeeNumber(),
                project.getPm().getName(),
                project.getStatus(),
                project.getProgressRate(),
                project.getPlannedStartDate(),
                project.getPlannedEndDate(),
                readStringList(project.getAcceptanceConditionsJson()),
                readStringList(project.getBudgetContractConditionsJson()),
                readStringList(project.getSecurityPrivacyConditionsJson()),
                readiness,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : List.copyOf(values);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROJECT_DATA_CORRUPTED",
                    "Stored project details could not be read.",
                    exception
            );
        }
    }

    private ApiException projectNotFound(Long projectId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND",
                "Project was not found. projectId=" + projectId
        );
    }
}
