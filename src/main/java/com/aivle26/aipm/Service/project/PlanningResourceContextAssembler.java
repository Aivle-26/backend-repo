package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignmentRecommendationRequest;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlanningResourceContextAssembler {

    private static final String ACTIVE_ALLOCATION_STATUS = "ACTIVE";
    private static final double DEFAULT_PM_AVAILABLE_HOURS_PER_WEEK = 32.0;

    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;

    @Transactional(readOnly = true)
    public PlanningResourceContext assembleForRecommendation(
            Long projectId,
            AssignmentRecommendationRequest request
    ) {
        Project project = requireProject(projectId);
        List<TaskContext> tasks = loadLeafTasksWithSchedules(projectId);
        CandidateSelection selection = selectCandidates(project, request);
        DateWindow allocationWindow = allocationWindow(tasks);
        Map<Long, CandidateContext> candidatesByAiId = assignTemporaryAiIds(
                selection.candidates(),
                allocationWindow
        );
        return new PlanningResourceContext(
                project,
                selection.mode(),
                tasks,
                candidatesByAiId,
                toAiRequest(project, tasks, candidatesByAiId),
                null,
                selection.excludedCandidateNames()
        );
    }

    @Transactional(readOnly = true)
    public PlanningResourceContext assembleForOrganizationChart(Long projectId) {
        Project project = requireProject(projectId);
        List<TaskContext> tasks = loadLeafTasksWithSchedules(projectId);
        List<ProjectMember> projectMembers = loadProjectMembers(projectId);
        Map<String, UserCapabilityProfile> profiles = loadProfiles(
                projectMembers.stream().map(ProjectMember::getUser).toList()
        );
        for (ProjectMember projectMember : projectMembers) {
            UserCapabilityProfile profile = profiles.get(
                    projectMember.getUser().getEmployeeNumber()
            );
            if (profile == null || profile.getRoles().isEmpty()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "MEMBER_CAPABILITY_NOT_FOUND",
                        "An active project member has no capability profile."
                );
            }
        }

        List<CandidateData> candidates = projectMembers.stream()
                .map(member -> new CandidateData(
                        member.getUser(),
                        profiles.get(member.getUser().getEmployeeNumber()),
                        member.getAvailableHoursPerWeek()
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        DateWindow allocationWindow = allocationWindow(tasks);
        Map<Long, CandidateContext> candidatesByAiId = assignTemporaryAiIds(
                candidates,
                allocationWindow
        );

        Long projectManagerAiId = candidatesByAiId.entrySet().stream()
                .filter(entry -> entry.getValue().user().getEmployeeNumber()
                        .equals(project.getPm().getEmployeeNumber()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (projectManagerAiId == null) {
            projectManagerAiId = (long) candidatesByAiId.size() + 1L;
            candidatesByAiId.put(
                    projectManagerAiId,
                    new CandidateContext(
                            projectManagerAiId,
                            project.getPm(),
                            null,
                            0.0,
                            allocationWindow
                    )
            );
        }

        Map<Long, CandidateContext> immutableCandidates = Collections.unmodifiableMap(
                new LinkedHashMap<>(candidatesByAiId)
        );
        return new PlanningResourceContext(
                project,
                AssignmentRecommendationResponse.CandidateMode.ALL,
                tasks,
                immutableCandidates,
                toAiRequest(project, tasks, immutableCandidates),
                projectManagerAiId,
                List.of()
        );
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
    }

    private List<TaskContext> loadLeafTasksWithSchedules(Long projectId) {
        List<ProjectWbsTask> confirmedTasks = new ArrayList<>(
                wbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId)
        );
        if (confirmedTasks.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONFIRMED_WBS_NOT_FOUND",
                    "Confirmed WBS tasks are required."
            );
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
                        Function.identity(),
                        (left, right) -> right
                ));

        List<ProjectWbsTask> leafTasks = confirmedTasks.stream()
                .filter(task -> !parentIds.contains(task.getId()))
                .sorted(Comparator.comparingInt(ProjectWbsTask::getOrderIndex)
                        .thenComparing(ProjectWbsTask::getId))
                .toList();
        if (leafTasks.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONFIRMED_WBS_NOT_FOUND",
                    "Confirmed leaf WBS tasks are required."
            );
        }

        return leafTasks.stream().map(task -> {
            ProjectSchedule schedule = schedulesByWbsId.get(task.getId());
            if (schedule == null) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "PLANNING_SCHEDULE_NOT_FOUND",
                        "A planning schedule is required for every leaf WBS task."
                );
            }
            return new TaskContext(task, schedule);
        }).toList();
    }

    private CandidateSelection selectCandidates(
            Project project,
            AssignmentRecommendationRequest request
    ) {
        List<AssignmentRecommendationRequest.Candidate> requested =
                request == null || request.candidates() == null
                        ? List.of()
                        : request.candidates();
        return requested.isEmpty()
                ? selectAllCandidates(project)
                : selectRequestedCandidates(project, requested);
    }

    private CandidateSelection selectAllCandidates(Project project) {
        List<ProjectMember> projectMembers = loadRecommendationMembers(project.getId());
        Map<String, UserCapabilityProfile> profiles = loadProfiles(
                candidateUsers(project, projectMembers)
        );
        List<CandidateData> candidates = new ArrayList<>();
        List<String> excludedNames = new ArrayList<>();
        for (ProjectMember member : projectMembers) {
            if (isProjectManager(project, member.getUser())) {
                continue;
            }
            UserCapabilityProfile profile = profiles.get(member.getUser().getEmployeeNumber());
            if (profile == null) {
                // 역량(역할) 미등록 팀원은 AI가 필요 역할과 매칭할 수 없어 후보에서 제외한다.
                // 조용히 빼면 "추천이 전부 같은 사람"으로 보이므로 warnings로 알린다.
                excludedNames.add(member.getUser().getName());
                continue;
            }
            candidates.add(new CandidateData(
                    member.getUser(),
                    profile,
                    member.getAvailableHoursPerWeek()
            ));
        }
        candidates.add(projectManagerCandidate(project, projectMembers, profiles, null));
        requireCandidates(candidates);
        return new CandidateSelection(
                AssignmentRecommendationResponse.CandidateMode.ALL,
                List.copyOf(candidates),
                List.copyOf(excludedNames)
        );
    }

    private CandidateSelection selectRequestedCandidates(
            Project project,
            List<AssignmentRecommendationRequest.Candidate> requested
    ) {
        Map<String, AssignmentRecommendationRequest.Candidate> requestedByEmployeeNumber =
                new LinkedHashMap<>();
        for (AssignmentRecommendationRequest.Candidate candidate : requested) {
            String employeeNumber = candidate.employeeNumber().trim();
            if (requestedByEmployeeNumber.putIfAbsent(employeeNumber, candidate) != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_ASSIGNMENT_CANDIDATE",
                        "Duplicate candidate employee number."
                );
            }
        }

        List<ProjectMember> projectMembers = loadRecommendationMembers(project.getId());
        Map<String, ProjectMember> membersByEmployeeNumber = projectMembers.stream()
                .collect(Collectors.toMap(
                        member -> member.getUser().getEmployeeNumber(),
                        Function.identity()
                ));
        Map<String, UserCapabilityProfile> profiles = loadProfiles(
                candidateUsers(project, projectMembers)
        );

        List<CandidateData> candidates = new ArrayList<>();
        for (Map.Entry<String, AssignmentRecommendationRequest.Candidate> entry
                : requestedByEmployeeNumber.entrySet()) {
            if (entry.getKey().equals(project.getPm().getEmployeeNumber())) {
                continue;
            }
            ProjectMember projectMember = membersByEmployeeNumber.get(entry.getKey());
            if (projectMember == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ACTIVE_PROJECT_MEMBER_NOT_FOUND",
                        "Requested candidate is not an active project member."
                );
            }
            UserCapabilityProfile profile = profiles.get(entry.getKey());
            if (profile == null || profile.getRoles().isEmpty()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "MEMBER_CAPABILITY_NOT_FOUND",
                        "Requested candidate has no capability profile."
                );
            }
            Double requestedHours = entry.getValue().availableHoursPerWeek();
            candidates.add(new CandidateData(
                    projectMember.getUser(),
                    profile,
                    requestedHours == null
                            ? projectMember.getAvailableHoursPerWeek()
                            : requestedHours
            ));
        }
        AssignmentRecommendationRequest.Candidate requestedPm = requestedByEmployeeNumber.get(
                project.getPm().getEmployeeNumber()
        );
        candidates.add(projectManagerCandidate(
                project,
                projectMembers,
                profiles,
                requestedPm == null ? null : requestedPm.availableHoursPerWeek()
        ));
        requireCandidates(candidates);
        return new CandidateSelection(
                AssignmentRecommendationResponse.CandidateMode.SELECTED,
                List.copyOf(candidates),
                List.of()
        );
    }

    private List<User> candidateUsers(Project project, List<ProjectMember> projectMembers) {
        Map<String, User> users = projectMembers.stream()
                .map(ProjectMember::getUser)
                .collect(Collectors.toMap(
                        User::getEmployeeNumber,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        users.putIfAbsent(project.getPm().getEmployeeNumber(), project.getPm());
        return List.copyOf(users.values());
    }

    private CandidateData projectManagerCandidate(
            Project project,
            List<ProjectMember> projectMembers,
            Map<String, UserCapabilityProfile> profiles,
            Double requestedHours
    ) {
        double savedHours = projectMembers.stream()
                .filter(member -> isProjectManager(project, member.getUser()))
                .map(ProjectMember::getAvailableHoursPerWeek)
                .findFirst()
                .orElse(DEFAULT_PM_AVAILABLE_HOURS_PER_WEEK);
        return new CandidateData(
                project.getPm(),
                profiles.get(project.getPm().getEmployeeNumber()),
                requestedHours == null ? savedHours : requestedHours
        );
    }

    private boolean isProjectManager(Project project, User user) {
        return project.getPm().getEmployeeNumber().equals(user.getEmployeeNumber());
    }

    private List<ProjectMember> loadProjectMembers(Long projectId) {
        List<ProjectMember> members = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        if (members.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_PROJECT_MEMBER_NOT_FOUND",
                    "At least one active project member is required."
            );
        }
        return members;
    }

    private List<ProjectMember> loadRecommendationMembers(Long projectId) {
        return projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
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
                    "MEMBER_CAPABILITY_NOT_FOUND",
                    "No active project member has a capability profile."
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
            result.put(nextId, new CandidateContext(
                    nextId,
                    candidate.user(),
                    candidate.profile(),
                    candidate.availableHoursPerWeek(),
                    allocationWindow
            ));
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
        return new PlanningResourceRecommendRequest(
                project.getId(),
                project.getName(),
                aiTasks,
                aiMembers
        );
    }

    private PlanningResourceRecommendRequest.ProjectMember toAiMember(
            CandidateContext context
    ) {
        List<String> roles = context.profile() == null
                ? List.of("PM")
                : context.profile().getRoles().stream().sorted().toList();
        List<PlanningResourceRecommendRequest.Skill> skills = context.profile() == null
                ? List.of()
                : context.profile().getSkills().stream()
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
                        context.availableHoursPerWeek(),
                        ACTIVE_ALLOCATION_STATUS
                );
        return new PlanningResourceRecommendRequest.ProjectMember(
                context.aiId(),
                context.user().getName(),
                roles,
                skills,
                List.of(allocation)
        );
    }

    public record PlanningResourceContext(
            Project project,
            AssignmentRecommendationResponse.CandidateMode candidateMode,
            List<TaskContext> tasks,
            Map<Long, CandidateContext> candidatesByAiId,
            PlanningResourceRecommendRequest aiRequest,
            Long projectManagerAiId,
            List<String> excludedCandidateNames
    ) {
    }

    public record TaskContext(ProjectWbsTask task, ProjectSchedule schedule) {
    }

    public record CandidateContext(
            Long aiId,
            User user,
            UserCapabilityProfile profile,
            double availableHoursPerWeek,
            DateWindow allocationWindow
    ) {
    }

    private record CandidateData(
            User user,
            UserCapabilityProfile profile,
            double availableHoursPerWeek
    ) {
    }

    private record CandidateSelection(
            AssignmentRecommendationResponse.CandidateMode mode,
            List<CandidateData> candidates,
            List<String> excludedCandidateNames
    ) {
    }

    public record DateWindow(LocalDate startDate, LocalDate endDate) {
    }
}
