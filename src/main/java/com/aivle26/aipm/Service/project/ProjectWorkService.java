package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignProjectTaskRequest;
import com.aivle26.aipm.Dto.project.ProjectProgressResponse;
import com.aivle26.aipm.Dto.project.ProjectProgressRateResponse;
import com.aivle26.aipm.Dto.project.ProjectSearchResponse;
import com.aivle26.aipm.Dto.project.SaveFinalTaskAssignmentsRequest;
import com.aivle26.aipm.Dto.project.TaskAssignmentResponse;
import com.aivle26.aipm.Dto.project.TeamProgressResponse;
import com.aivle26.aipm.Dto.project.UpdateTaskProgressRequest;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectMessage;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectMessageRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectWorkService {
    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectDocumentRepository documentRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final ProjectMessageRepository messageRepository;
    private final ProjectArtifactRepository artifactRepository;

    @Transactional
    public TaskAssignmentResponse assignTask(
            Long projectId,
            Long wbsId,
            AssignProjectTaskRequest request
    ) {
        authorizationService.requireProjectPm(projectId);
        Project project = requireProject(projectId);
        ProjectWbsTask task = requireConfirmedTask(projectId, wbsId);
        ProjectSchedule schedule = requireSchedule(projectId, wbsId);
        String employeeNumber = request.employeeNumber().trim();
        if (!projectMemberRepository.existsByProjectIdAndUser_EmployeeNumberAndActiveTrue(
                projectId, employeeNumber)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PROJECT_TEAM_MEMBER_NOT_FOUND",
                    "Project team member was not found. employeeNumber=" + employeeNumber
            );
        }

        LocalDate dueDate = request.dueDate() == null ? schedule.getEndDate() : request.dueDate();
        if (dueDate.isBefore(schedule.getStartDate())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_TASK_DUE_DATE",
                    "Task due date cannot be before its schedule start date."
            );
        }

        ProjectTaskAssignment assignment = assignmentRepository
                .findByProjectIdAndWbsTaskId(projectId, wbsId)
                .orElseGet(ProjectTaskAssignment::new);
        if (assignment.getId() == null) {
            assignment.setProject(project);
            assignment.setWbsTask(task);
            assignment.setStatus(TaskProgressStatus.TODO);
            assignment.setProgressRate(0);
        } else if (!employeeNumber.equals(assignment.getEmployeeNumber())) {
            assignment.setStatus(TaskProgressStatus.TODO);
            assignment.setProgressRate(0);
        }
        assignment.setEmployeeNumber(employeeNumber);
        assignment.setDueDate(dueDate);
        assignment.setAssignedHours((double) Math.max(1, task.getEstimatedHours()));
        assignment.setAssignedBy(authorizationService.currentUser().employeeNumber());
        ProjectTaskAssignment saved = assignmentRepository.save(assignment);
        updateStoredProjectProgress(project, leafTasks(projectId));
        return toResponse(saved, schedule, LocalDate.now());
    }

    @Transactional
    public List<TaskAssignmentResponse> replaceFinalAssignments(
            Long projectId,
            SaveFinalTaskAssignmentsRequest request
    ) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        authorizationService.requireProjectPm(project);

        List<ProjectWbsTask> tasks = leafTasks(projectId);
        if (tasks.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONFIRMED_WBS_TASK_NOT_FOUND",
                    "Confirmed leaf WBS tasks were not found."
            );
        }

        Map<Long, ProjectWbsTask> tasksById = tasks.stream()
                .collect(Collectors.toMap(
                        ProjectWbsTask::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, SaveFinalTaskAssignmentsRequest.Assignment> requestedByWbsId =
                normalizeFinalAssignments(request.assignments());
        validateCompleteAssignmentSet(tasksById.keySet(), requestedByWbsId.keySet());

        Map<Long, ProjectSchedule> schedulesByWbsId = scheduleRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.toMap(
                        schedule -> schedule.getWbsTask().getId(),
                        Function.identity()
                ));
        Set<String> activeMembers = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId)
                .stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(Collectors.toSet());
        Map<Long, ProjectTaskAssignment> existingByWbsId = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getWbsTask().getId(),
                        Function.identity()
                ));

        String assignedBy = authorizationService.currentUser().employeeNumber();
        List<ProjectTaskAssignment> finalAssignments = new ArrayList<>();
        for (ProjectWbsTask task : tasks) {
            SaveFinalTaskAssignmentsRequest.Assignment requested = requestedByWbsId.get(task.getId());
            String employeeNumber = requested.employeeNumber().trim();
            if (!activeMembers.contains(employeeNumber)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PROJECT_TEAM_MEMBER_NOT_FOUND",
                        "Project team member was not found. employeeNumber=" + employeeNumber
                );
            }

            ProjectSchedule schedule = schedulesByWbsId.get(task.getId());
            if (schedule == null) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "PROJECT_SCHEDULE_NOT_FOUND",
                        "Project schedule was not found. wbsId=" + task.getId()
                );
            }
            LocalDate dueDate = requested.dueDate() == null
                    ? schedule.getEndDate()
                    : requested.dueDate();
            validateDueDate(schedule, dueDate);
            double assignedHours = resolveAssignedHours(task, requested.assignedHours());

            ProjectTaskAssignment assignment = existingByWbsId.remove(task.getId());
            if (assignment == null) {
                assignment = new ProjectTaskAssignment();
                assignment.setProject(project);
                assignment.setWbsTask(task);
                assignment.setStatus(TaskProgressStatus.TODO);
                assignment.setProgressRate(0);
            } else if (!employeeNumber.equals(assignment.getEmployeeNumber())) {
                assignment.setStatus(TaskProgressStatus.TODO);
                assignment.setProgressRate(0);
            }
            assignment.setEmployeeNumber(employeeNumber);
            assignment.setDueDate(dueDate);
            assignment.setAssignedHours(assignedHours);
            assignment.setAssignedBy(assignedBy);
            finalAssignments.add(assignment);
        }

        if (!existingByWbsId.isEmpty()) {
            assignmentRepository.deleteAll(existingByWbsId.values());
        }
        List<ProjectTaskAssignment> saved = assignmentRepository.saveAll(finalAssignments);
        updateStoredProjectProgress(project, tasks);
        return mapAssignments(projectId, saved);
    }

    @Transactional(readOnly = true)
    public List<TaskAssignmentResponse> getAssignments(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        return mapAssignments(
                projectId,
                assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
        );
    }

    @Transactional(readOnly = true)
    public ProjectProgressResponse getProjectProgress(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        List<ProjectWbsTask> tasks = leafTasks(projectId);
        Map<Long, ProjectTaskAssignment> assignments = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.toMap(a -> a.getWbsTask().getId(), Function.identity()));
        return calculateProgress(projectId, null, tasks, assignments);
    }

    @Transactional(readOnly = true)
    public ProjectProgressRateResponse getStoredProjectProgressRate(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        Project project = requireProject(projectId);
        return new ProjectProgressRateResponse(
                project.getId(),
                project.getProgressRate(),
                project.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ProjectProgressResponse getMyProgress(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        String employeeNumber = authorizationService.currentUser().employeeNumber();
        List<ProjectTaskAssignment> assignments = assignmentRepository
                .findByProjectIdAndEmployeeNumberOrderByWbsTask_OrderIndexAscIdAsc(
                        projectId, employeeNumber);
        Map<Long, ProjectTaskAssignment> byTask = assignments.stream()
                .collect(Collectors.toMap(a -> a.getWbsTask().getId(), Function.identity()));
        return calculateProgress(
                projectId,
                employeeNumber,
                assignments.stream().map(ProjectTaskAssignment::getWbsTask).toList(),
                byTask
        );
    }

    @Transactional(readOnly = true)
    public TeamProgressResponse getTeamProgress(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        Map<String, List<ProjectTaskAssignment>> assignmentsByMember = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        ProjectTaskAssignment::getEmployeeNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Set<String> employeeNumbers = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId)
                .stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        employeeNumbers.addAll(assignmentsByMember.keySet());

        List<TeamProgressResponse.MemberProgress> members = employeeNumbers.stream()
                .map(employeeNumber -> memberProgress(
                        employeeNumber,
                        assignmentsByMember.getOrDefault(employeeNumber, List.of())
                ))
                .sorted(Comparator.comparing(TeamProgressResponse.MemberProgress::name)
                        .thenComparing(TeamProgressResponse.MemberProgress::employeeNumber))
                .toList();
        return new TeamProgressResponse(projectId, members);
    }

    @Transactional(readOnly = true)
    public List<TaskAssignmentResponse> getMyTasks(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        String employeeNumber = authorizationService.currentUser().employeeNumber();
        return mapAssignments(
                projectId,
                assignmentRepository.findByProjectIdAndEmployeeNumberOrderByWbsTask_OrderIndexAscIdAsc(
                        projectId, employeeNumber)
        );
    }

    @Transactional(readOnly = true)
    public List<TaskAssignmentResponse> getDueSoonTasks(Long projectId, int days) {
        authorizationService.requireProjectAccess(projectId);
        if (days < 0 || days > 90) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DUE_SOON_DAYS", "days must be between 0 and 90.");
        }
        LocalDate today = LocalDate.now();
        List<ProjectTaskAssignment> assignments = assignmentRepository
                .findByProjectIdAndDueDateBetweenOrderByDueDateAscWbsTask_OrderIndexAsc(
                        projectId, today, today.plusDays(days));
        AuthenticatedUser user = authorizationService.currentUser();
        if (!"PM".equals(user.role())) {
            assignments = assignments.stream()
                    .filter(assignment -> user.employeeNumber().equals(assignment.getEmployeeNumber()))
                    .toList();
        }
        return mapAssignments(
                projectId,
                assignments.stream()
                        .filter(assignment -> assignment.getStatus() != TaskProgressStatus.COMPLETED)
                        .toList()
        );
    }

    @Transactional
    public TaskAssignmentResponse updateProgress(
            Long projectId,
            Long wbsId,
            UpdateTaskProgressRequest request
    ) {
        authorizationService.requireProjectAccess(projectId);
        ProjectTaskAssignment assignment = assignmentRepository
                .findByProjectIdAndWbsTaskId(projectId, wbsId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TASK_ASSIGNMENT_NOT_FOUND",
                        "Task assignment was not found."
                ));
        AuthenticatedUser user = authorizationService.currentUser();
        if (!"PM".equals(user.role())
                && !user.employeeNumber().equals(assignment.getEmployeeNumber())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_PROGRESS_FORBIDDEN", "Task progress cannot be updated.");
        }
        validateProgress(request);
        assignment.setStatus(request.status());
        assignment.setProgressRate(request.status() == TaskProgressStatus.COMPLETED
                ? 100
                : request.progressRate());
        ProjectTaskAssignment saved = assignmentRepository.save(assignment);
        updateStoredProjectProgress(assignment.getProject(), leafTasks(projectId));
        return toResponse(
                saved,
                requireSchedule(projectId, wbsId),
                LocalDate.now()
        );
    }

    @Transactional(readOnly = true)
    public ProjectSearchResponse search(Long projectId, String query, int limit) {
        authorizationService.requireProjectAccess(projectId);
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "query must contain at least 2 characters.");
        }
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_LIMIT", "limit must be between 1 and 100.");
        }
        String needle = normalized.toLowerCase(Locale.ROOT);
        List<ProjectSearchResponse.SearchResult> results = new ArrayList<>();
        Project project = requireProject(projectId);
        addIfMatch(results, needle, "PROJECT", String.valueOf(projectId), project.getName(), project.getDescription());
        for (ProjectDocument document : documentRepository.findByProjectId(projectId)) {
            addIfMatch(results, needle, "DOCUMENT", String.valueOf(document.getId()),
                    document.getOriginalFileName(), document.getFileType());
        }
        for (ProjectRequirement requirement : requirementRepository.findByProjectIdOrderByIdAsc(projectId)) {
            addIfMatch(results, needle, "REQUIREMENT", String.valueOf(requirement.getId()),
                    requirement.getTitle(), requirement.getDescription());
        }
        for (ProjectWbsTask task : wbsTaskRepository.findByProjectIdOrderByOrderIndexAscIdAsc(projectId)) {
            addIfMatch(results, needle, "WBS_TASK", String.valueOf(task.getId()), task.getTaskName(), task.getDescription());
        }
        for (ProjectMessage message : messageRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)) {
            addIfMatch(results, needle, "MESSAGE", String.valueOf(message.getId()), message.getTitle(), message.getContent());
        }
        for (ProjectArtifact artifact : artifactRepository.findByProjectId(projectId)) {
            addIfMatch(results, needle, "ARTIFACT", String.valueOf(artifact.getId()),
                    artifact.getArtifactName(), artifact.getArtifactType().name());
        }
        List<ProjectSearchResponse.SearchResult> limited = results.stream().limit(limit).toList();
        return new ProjectSearchResponse(projectId, normalized, results.size(), limited);
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found."));
    }

    private ProjectWbsTask requireConfirmedTask(Long projectId, Long wbsId) {
        return wbsTaskRepository.findById(wbsId)
                .filter(task -> task.getProject().getId().equals(projectId) && task.isConfirmed())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONFIRMED_WBS_TASK_NOT_FOUND", "Confirmed WBS task was not found."));
    }

    private ProjectSchedule requireSchedule(Long projectId, Long wbsId) {
        return scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId).stream()
                .filter(schedule -> schedule.getWbsTask().getId().equals(wbsId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PROJECT_SCHEDULE_NOT_FOUND", "Project schedule was not found."));
    }

    private Map<Long, SaveFinalTaskAssignmentsRequest.Assignment> normalizeFinalAssignments(
            List<SaveFinalTaskAssignmentsRequest.Assignment> assignments
    ) {
        Map<Long, SaveFinalTaskAssignmentsRequest.Assignment> normalized = new LinkedHashMap<>();
        for (SaveFinalTaskAssignmentsRequest.Assignment assignment : assignments) {
            if (normalized.putIfAbsent(assignment.wbsId(), assignment) != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_WBS_ASSIGNMENT",
                        "Duplicate WBS assignment. wbsId=" + assignment.wbsId()
                );
            }
        }
        return normalized;
    }

    private void validateCompleteAssignmentSet(Set<Long> requiredWbsIds, Set<Long> requestedWbsIds) {
        Set<Long> missingWbsIds = new LinkedHashSet<>(requiredWbsIds);
        missingWbsIds.removeAll(requestedWbsIds);
        Set<Long> invalidWbsIds = new LinkedHashSet<>(requestedWbsIds);
        invalidWbsIds.removeAll(requiredWbsIds);
        if (!missingWbsIds.isEmpty() || !invalidWbsIds.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INCOMPLETE_FINAL_ASSIGNMENTS",
                    "Final assignments must contain every confirmed leaf WBS exactly once. "
                            + "missingWbsIds=" + missingWbsIds
                            + ", invalidWbsIds=" + invalidWbsIds
            );
        }
    }

    private void validateDueDate(ProjectSchedule schedule, LocalDate dueDate) {
        if (dueDate.isBefore(schedule.getStartDate())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_TASK_DUE_DATE",
                    "Task due date cannot be before its schedule start date."
            );
        }
    }

    private double resolveAssignedHours(ProjectWbsTask task, Double requestedHours) {
        double assignedHours = requestedHours == null
                ? Math.max(1, task.getEstimatedHours())
                : requestedHours;
        if (!Double.isFinite(assignedHours) || assignedHours <= 0 || assignedHours > 100_000) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_ASSIGNED_HOURS",
                    "Assigned hours must be greater than 0 and at most 100000."
            );
        }
        return assignedHours;
    }

    private List<ProjectWbsTask> leafTasks(Long projectId) {
        List<ProjectWbsTask> tasks = wbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId);
        Set<Long> parentIds = tasks.stream()
                .map(ProjectWbsTask::getParentTask)
                .filter(parent -> parent != null)
                .map(ProjectWbsTask::getId)
                .collect(Collectors.toSet());
        return tasks.stream()
                .filter(task -> !parentIds.contains(task.getId()))
                .sorted(Comparator.comparingInt(ProjectWbsTask::getOrderIndex).thenComparing(ProjectWbsTask::getId))
                .toList();
    }

    private ProjectProgressResponse calculateProgress(
            Long projectId,
            String employeeNumber,
            List<ProjectWbsTask> tasks,
            Map<Long, ProjectTaskAssignment> assignments
    ) {
        int totalHours = tasks.stream().mapToInt(task -> Math.max(1, task.getEstimatedHours())).sum();
        int progress = calculateProgressRate(tasks, assignments);
        LocalDate today = LocalDate.now();
        int completed = (int) assignments.values().stream().filter(a -> a.getStatus() == TaskProgressStatus.COMPLETED).count();
        int delayed = (int) assignments.values().stream().filter(a -> isDelayed(a, today)).count();
        int completedHours = assignments.values().stream()
                .filter(a -> a.getStatus() == TaskProgressStatus.COMPLETED)
                .mapToInt(a -> Math.max(1, a.getWbsTask().getEstimatedHours()))
                .sum();
        return new ProjectProgressResponse(projectId, employeeNumber, progress, tasks.size(), assignments.size(),
                completed, delayed, totalHours, completedHours);
    }

    private void updateStoredProjectProgress(Project project, List<ProjectWbsTask> tasks) {
        Map<Long, ProjectTaskAssignment> assignments = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(project.getId())
                .stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getWbsTask().getId(),
                        Function.identity()
                ));
        project.setProgressRate(calculateProgressRate(tasks, assignments));
        projectRepository.save(project);
    }

    private int calculateProgressRate(
            List<ProjectWbsTask> tasks,
            Map<Long, ProjectTaskAssignment> assignments
    ) {
        int totalHours = tasks.stream()
                .mapToInt(task -> Math.max(1, task.getEstimatedHours()))
                .sum();
        if (totalHours == 0) {
            return 0;
        }
        long weightedProgress = tasks.stream()
                .mapToLong(task -> {
                    ProjectTaskAssignment assignment = assignments.get(task.getId());
                    int taskProgressRate = assignment == null ? 0 : assignment.getProgressRate();
                    return (long) Math.max(1, task.getEstimatedHours()) * taskProgressRate;
                })
                .sum();
        return (int) Math.round((double) weightedProgress / totalHours);
    }

    private TeamProgressResponse.MemberProgress memberProgress(
            String employeeNumber,
            List<ProjectTaskAssignment> assignments
    ) {
        int totalHours = assignments.stream().mapToInt(a -> Math.max(1, a.getWbsTask().getEstimatedHours())).sum();
        long weighted = assignments.stream().mapToLong(a ->
                (long) Math.max(1, a.getWbsTask().getEstimatedHours()) * a.getProgressRate()).sum();
        int progress = totalHours == 0 ? 0 : (int) Math.round((double) weighted / totalHours);
        int completed = (int) assignments.stream().filter(a -> a.getStatus() == TaskProgressStatus.COMPLETED).count();
        int delayed = (int) assignments.stream().filter(a -> isDelayed(a, LocalDate.now())).count();
        String name = userRepository.findByEmployeeNumber(employeeNumber).map(User::getName).orElse(employeeNumber);
        return new TeamProgressResponse.MemberProgress(employeeNumber, name, progress,
                assignments.size(), completed, delayed, totalHours);
    }

    private List<TaskAssignmentResponse> mapAssignments(Long projectId, List<ProjectTaskAssignment> assignments) {
        Map<Long, ProjectSchedule> schedules = scheduleRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream().collect(Collectors.toMap(s -> s.getWbsTask().getId(), Function.identity()));
        LocalDate today = LocalDate.now();
        return assignments.stream()
                .map(assignment -> toResponse(assignment, schedules.get(assignment.getWbsTask().getId()), today))
                .toList();
    }

    private TaskAssignmentResponse toResponse(
            ProjectTaskAssignment assignment,
            ProjectSchedule schedule,
            LocalDate today
    ) {
        ProjectWbsTask task = assignment.getWbsTask();
        return new TaskAssignmentResponse(
                assignment.getId(), assignment.getProject().getId(), task.getId(), task.getTaskCode(),
                task.getTaskName(), task.getDescription(), assignment.getEmployeeNumber(), assignment.getStatus(),
                assignment.getProgressRate(), schedule == null ? null : schedule.getStartDate(), assignment.getDueDate(),
                task.getEstimatedHours(), assignment.getAssignedHours() == null
                        ? Math.max(1, task.getEstimatedHours())
                        : assignment.getAssignedHours(),
                assignment.getAssignedBy(), schedule != null && schedule.isMilestone(),
                schedule == null ? 0 : schedule.getBufferDays(), isDelayed(assignment, today),
                assignment.getAssignedAt(), assignment.getUpdatedAt()
        );
    }

    private boolean isDelayed(ProjectTaskAssignment assignment, LocalDate today) {
        return assignment.getStatus() == TaskProgressStatus.DELAYED
                || assignment.getStatus() != TaskProgressStatus.COMPLETED
                && assignment.getDueDate().isBefore(today);
    }

    private void validateProgress(UpdateTaskProgressRequest request) {
        if (request.status() == TaskProgressStatus.COMPLETED && request.progressRate() != 100) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TASK_PROGRESS", "Completed task progress must be 100.");
        }
        if (request.status() == TaskProgressStatus.TODO && request.progressRate() != 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TASK_PROGRESS", "TODO task progress must be 0.");
        }
    }

    private void addIfMatch(
            List<ProjectSearchResponse.SearchResult> results,
            String needle,
            String type,
            String id,
            String title,
            String summary
    ) {
        String searchable = ((title == null ? "" : title) + " " + (summary == null ? "" : summary))
                .toLowerCase(Locale.ROOT);
        if (searchable.contains(needle)) {
            results.add(new ProjectSearchResponse.SearchResult(type, id, title, abbreviate(summary)));
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
