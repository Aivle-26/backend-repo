package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignmentRecommendationRequest;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentRecommendationService {

    private static final String ACTIVE_ALLOCATION_STATUS = "ACTIVE";

    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;
    private final PlanningResourceClient planningResourceClient;

    @Transactional(readOnly = true)
    public AssignmentRecommendationResponse recommend(
            Long projectId,
            AssignmentRecommendationRequest request
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        List<TaskContext> tasks = loadLeafTasksWithSchedules(projectId);
        CandidateSelection selection = selectCandidates(projectId, request);
        DateWindow allocationWindow = allocationWindow(tasks);
        Map<Long, CandidateContext> candidatesByAiId = assignTemporaryAiIds(
                selection.candidates(),
                allocationWindow
        );

        PlanningResourceRecommendRequest aiRequest = toAiRequest(
                project,
                tasks,
                candidatesByAiId
        );
        PlanningResourceRecommendResponse aiResponse =
                planningResourceClient.recommendAssignments(aiRequest);
        return toFrontendResponse(
                project,
                selection.mode(),
                tasks,
                candidatesByAiId,
                aiResponse
        );
    }

    private List<TaskContext> loadLeafTasksWithSchedules(Long projectId) {
        List<ProjectWbsTask> confirmedTasks = new ArrayList<>(
                wbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId)
        );
        if (confirmedTasks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        Set<Long> parentIds = confirmedTasks.stream()
                .map(ProjectWbsTask::getParentTask)
                .filter(parent -> parent != null)
                .map(ProjectWbsTask::getId)
                .collect(Collectors.toSet());
        Map<Long, ProjectSchedule> schedulesByWbsId = scheduleRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.toMap(
                        schedule -> schedule.getWbsTask().getId(),
                        Function.identity()
                ));

        List<TaskContext> tasks = confirmedTasks.stream()
                .filter(task -> !parentIds.contains(task.getId()))
                .sorted(Comparator.comparingInt(ProjectWbsTask::getOrderIndex)
                        .thenComparing(ProjectWbsTask::getId))
                .map(task -> {
                    ProjectSchedule schedule = schedulesByWbsId.get(task.getId());
                    if (schedule == null) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "recommended schedule not found for wbs: " + task.getId()
                        );
                    }
                    return new TaskContext(task, schedule);
                })
                .toList();
        if (tasks.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "assignable wbs task not found");
        }
        return tasks;
    }

    private CandidateSelection selectCandidates(
            Long projectId,
            AssignmentRecommendationRequest request
    ) {
        List<AssignmentRecommendationRequest.Candidate> requested =
                request == null || request.candidates() == null
                        ? List.of()
                        : request.candidates();
        if (requested.isEmpty()) {
            return selectAllCandidates(projectId);
        }
        return selectRequestedCandidates(projectId, requested);
    }

    private CandidateSelection selectAllCandidates(Long projectId) {
        List<ProjectMember> projectMembers = loadProjectMembers(projectId);
        List<User> users = projectMembers.stream().map(ProjectMember::getUser).toList();
        Map<String, UserCapabilityProfile> profiles = loadProfiles(users);
        List<CandidateData> candidates = projectMembers.stream()
                .filter(member -> profiles.containsKey(member.getUser().getEmployeeNumber()))
                .map(member -> new CandidateData(
                        member.getUser(),
                        profiles.get(member.getUser().getEmployeeNumber()),
                        member.getAvailableHoursPerWeek()
                ))
                .toList();
        requireCandidates(candidates);
        return new CandidateSelection(
                AssignmentRecommendationResponse.CandidateMode.ALL,
                candidates
        );
    }

    private CandidateSelection selectRequestedCandidates(
            Long projectId,
            List<AssignmentRecommendationRequest.Candidate> requested
    ) {
        Map<String, AssignmentRecommendationRequest.Candidate> requestedByEmployeeNumber =
                new LinkedHashMap<>();
        for (AssignmentRecommendationRequest.Candidate candidate : requested) {
            String employeeNumber = candidate.employeeNumber().trim();
            if (requestedByEmployeeNumber.putIfAbsent(employeeNumber, candidate) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate candidate employee number");
            }
        }

        List<ProjectMember> projectMembers = loadProjectMembers(projectId);
        Map<String, ProjectMember> membersByEmployeeNumber = projectMembers.stream()
                .collect(Collectors.toMap(
                        member -> member.getUser().getEmployeeNumber(),
                        Function.identity()
                ));
        Map<String, UserCapabilityProfile> profiles = loadProfiles(
                projectMembers.stream().map(ProjectMember::getUser).toList()
        );

        List<CandidateData> candidates = new ArrayList<>();
        for (Map.Entry<String, AssignmentRecommendationRequest.Candidate> entry
                : requestedByEmployeeNumber.entrySet()) {
            ProjectMember projectMember = membersByEmployeeNumber.get(entry.getKey());
            if (projectMember == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "active project member not found: " + entry.getKey()
                );
            }
            User user = projectMember.getUser();
            UserCapabilityProfile profile = profiles.get(entry.getKey());
            if (profile == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "team member capabilities not registered: " + entry.getKey()
                );
            }
            Double requestedHours = entry.getValue().availableHoursPerWeek();
            candidates.add(new CandidateData(
                    user,
                    profile,
                    requestedHours == null
                            ? projectMember.getAvailableHoursPerWeek()
                            : requestedHours
            ));
        }
        requireCandidates(candidates);
        return new CandidateSelection(
                AssignmentRecommendationResponse.CandidateMode.SELECTED,
                List.copyOf(candidates)
        );
    }

    private List<ProjectMember> loadProjectMembers(Long projectId) {
        List<ProjectMember> members = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        if (members.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "active project member not found");
        }
        return members;
    }

    private Map<String, UserCapabilityProfile> loadProfiles(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        return capabilityProfileRepository.findAllByEmployeeNumberIn(
                        users.stream().map(User::getEmployeeNumber).toList()
                ).stream()
                .filter(profile -> !profile.getRoles().isEmpty())
                .collect(Collectors.toMap(
                        UserCapabilityProfile::getEmployeeNumber,
                        Function.identity()
                ));
    }

    private void requireCandidates(List<CandidateData> candidates) {
        if (candidates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "team member with registered capabilities not found"
            );
        }
    }

    private DateWindow allocationWindow(List<TaskContext> tasks) {
        LocalDate startDate = tasks.stream()
                .map(task -> task.schedule().getStartDate())
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate endDate = tasks.stream()
                .map(task -> task.schedule().getEndDate())
                .max(LocalDate::compareTo)
                .orElseThrow();
        return new DateWindow(startDate, endDate);
    }

    private Map<Long, CandidateContext> assignTemporaryAiIds(
            List<CandidateData> candidates,
            DateWindow allocationWindow
    ) {
        Map<Long, CandidateContext> result = new LinkedHashMap<>();
        long nextId = 1L;
        for (CandidateData candidate : candidates) {
            result.put(nextId, new CandidateContext(nextId, candidate, allocationWindow));
            nextId++;
        }
        return result;
    }

    private PlanningResourceRecommendRequest toAiRequest(
            Project project,
            List<TaskContext> tasks,
            Map<Long, CandidateContext> candidatesByAiId
    ) {
        List<PlanningResourceRecommendRequest.WbsTask> aiTasks = tasks.stream()
                .map(task -> new PlanningResourceRecommendRequest.WbsTask(
                        task.task().getId(),
                        task.task().getTaskName(),
                        task.task().getDescription(),
                        task.schedule().getStartDate(),
                        task.schedule().getEndDate()
                ))
                .toList();
        List<PlanningResourceRecommendRequest.ProjectMember> aiMembers =
                candidatesByAiId.values().stream()
                        .map(this::toAiMember)
                        .toList();
        return new PlanningResourceRecommendRequest(project.getId(), aiTasks, aiMembers);
    }

    private PlanningResourceRecommendRequest.ProjectMember toAiMember(
            CandidateContext context
    ) {
        CandidateData candidate = context.candidate();
        List<PlanningResourceRecommendRequest.Skill> skills =
                candidate.profile().getSkills().stream()
                        .map(skill -> new PlanningResourceRecommendRequest.Skill(
                                skill.getSkillCode(),
                                skill.getProficiencyLevel(),
                                skill.getExperienceMonths()
                        ))
                        .toList();
        PlanningResourceRecommendRequest.Allocation allocation =
                new PlanningResourceRecommendRequest.Allocation(
                        context.allocationWindow().startDate(),
                        context.allocationWindow().endDate(),
                        candidate.availableHoursPerWeek(),
                        ACTIVE_ALLOCATION_STATUS
                );
        return new PlanningResourceRecommendRequest.ProjectMember(
                context.aiId(),
                candidate.profile().getRoles().stream().sorted().toList(),
                skills,
                List.of(allocation)
        );
    }

    private AssignmentRecommendationResponse toFrontendResponse(
            Project project,
            AssignmentRecommendationResponse.CandidateMode mode,
            List<TaskContext> tasks,
            Map<Long, CandidateContext> candidatesByAiId,
            PlanningResourceRecommendResponse aiResponse
    ) {
        validateAiResponse(project, tasks, candidatesByAiId, aiResponse);
        Map<Long, ProjectWbsTask> tasksById = tasks.stream()
                .map(TaskContext::task)
                .collect(Collectors.toMap(ProjectWbsTask::getId, Function.identity()));

        List<AssignmentRecommendationResponse.Assignment> assignments =
                aiResponse.assignments().stream()
                        .map(assignment -> toFrontendAssignment(
                                assignment,
                                tasksById.get(assignment.wbsId()),
                                candidatesByAiId
                        ))
                        .toList();
        List<AssignmentRecommendationResponse.Candidate> candidates =
                candidatesByAiId.values().stream()
                        .map(context -> {
                            User user = context.candidate().user();
                            return new AssignmentRecommendationResponse.Candidate(
                                    user.getEmployeeNumber(),
                                    user.getName(),
                                    user.getEmail(),
                                    context.candidate().availableHoursPerWeek()
                            );
                        })
                        .toList();

        return new AssignmentRecommendationResponse(
                project.getId(),
                mode,
                candidates,
                aiResponse.requiredStaffing(),
                assignments,
                aiResponse.totalEstimatedPersonDays(),
                aiResponse.totalEstimatedHours(),
                aiResponse.totalEstimatedMm(),
                aiResponse.unassignedWbsIds(),
                aiResponse.warnings(),
                aiResponse.llmStatus()
        );
    }

    private AssignmentRecommendationResponse.Assignment toFrontendAssignment(
            PlanningResourceRecommendResponse.Assignment assignment,
            ProjectWbsTask task,
            Map<Long, CandidateContext> candidatesByAiId
    ) {
        List<AssignmentRecommendationResponse.RecommendedMember> recommendedMembers =
                assignment.recommendedMembers().stream()
                        .map(recommended -> {
                            CandidateData candidate = candidatesByAiId
                                    .get(recommended.projectMemberId())
                                    .candidate();
                            User user = candidate.user();
                            return new AssignmentRecommendationResponse.RecommendedMember(
                                    user.getEmployeeNumber(),
                                    user.getName(),
                                    user.getEmail(),
                                    recommended.recommendationScore(),
                                    recommended.assignedHours(),
                                    recommended.remainingAvailableHours()
                            );
                        })
                        .toList();
        return new AssignmentRecommendationResponse.Assignment(
                assignment.wbsId(),
                task.getTaskName(),
                assignment.requiredRoleCode(),
                assignment.requiredSkills(),
                assignment.estimatedPersonDays(),
                assignment.estimatedHours(),
                assignment.estimatedMm(),
                assignment.requiredHeadcount(),
                recommendedMembers,
                assignment.recommendationReason()
        );
    }

    private void validateAiResponse(
            Project project,
            List<TaskContext> tasks,
            Map<Long, CandidateContext> candidatesByAiId,
            PlanningResourceRecommendResponse response
    ) {
        if (response == null || !project.getId().equals(response.projectId())) {
            throw invalidAiResponse();
        }
        if (response.requiredStaffing() == null
                || response.assignments() == null
                || response.unassignedWbsIds() == null
                || response.warnings() == null) {
            throw invalidAiResponse();
        }
        Set<Long> taskIds = tasks.stream()
                .map(task -> task.task().getId())
                .collect(Collectors.toSet());
        Set<Long> assignmentIds = new LinkedHashSet<>();
        for (PlanningResourceRecommendResponse.Assignment assignment : response.assignments()) {
            if (assignment == null
                    || !taskIds.contains(assignment.wbsId())
                    || !assignmentIds.add(assignment.wbsId())
                    || assignment.requiredSkills() == null
                    || assignment.recommendedMembers() == null) {
                throw invalidAiResponse();
            }
            for (PlanningResourceRecommendResponse.RecommendedMember member
                    : assignment.recommendedMembers()) {
                if (member == null || !candidatesByAiId.containsKey(member.projectMemberId())) {
                    throw invalidAiResponse();
                }
            }
        }
        if (response.unassignedWbsIds().stream().anyMatch(id -> !taskIds.contains(id))) {
            throw invalidAiResponse();
        }
    }

    private ApiException invalidAiResponse() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_RESOURCE_RESPONSE",
                "AI Server의 담당자 추천 결과 형식이 올바르지 않습니다."
        );
    }

    private record TaskContext(ProjectWbsTask task, ProjectSchedule schedule) {
    }

    private record CandidateData(
            User user,
            UserCapabilityProfile profile,
            double availableHoursPerWeek
    ) {
    }

    private record CandidateSelection(
            AssignmentRecommendationResponse.CandidateMode mode,
            List<CandidateData> candidates
    ) {
    }

    private record CandidateContext(
            Long aiId,
            CandidateData candidate,
            DateWindow allocationWindow
    ) {
    }

    private record DateWindow(LocalDate startDate, LocalDate endDate) {
    }
}
