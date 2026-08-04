package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignProjectTaskRequest;
import com.aivle26.aipm.Dto.project.SaveFinalTaskAssignmentsRequest;
import com.aivle26.aipm.Dto.project.UpdateTaskProgressRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkServiceTest {
    private static final Long PROJECT_ID = 10L;
    private static final Long WBS_ID = 20L;

    @Mock private ProjectAuthorizationService authorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectWbsTaskRepository wbsTaskRepository;
    @Mock private ProjectScheduleRepository scheduleRepository;
    @Mock private ProjectTaskAssignmentRepository assignmentRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectDocumentRepository documentRepository;
    @Mock private ProjectRequirementRepository requirementRepository;
    @Mock private ProjectMessageRepository messageRepository;
    @Mock private ProjectArtifactRepository artifactRepository;

    @InjectMocks private ProjectWorkService service;

    @Test
    void assignTaskPersistsPmConfirmedAssignment() {
        Project project = project();
        ProjectWbsTask task = task(project, WBS_ID, 40);
        ProjectSchedule schedule = schedule(project, task);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(wbsTaskRepository.findById(WBS_ID)).thenReturn(Optional.of(task));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(schedule));
        when(projectMemberRepository.existsByProjectIdAndUser_EmployeeNumberAndActiveTrue(
                PROJECT_ID, "STAFF001")).thenReturn(true);
        when(assignmentRepository.findByProjectIdAndWbsTaskId(PROJECT_ID, WBS_ID))
                .thenReturn(Optional.empty());
        when(authorizationService.currentUser())
                .thenReturn(new AuthenticatedUser("PM001", "PM"));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            ProjectTaskAssignment assignment = invocation.getArgument(0);
            assignment.setId(30L);
            return assignment;
        });

        var response = service.assignTask(
                PROJECT_ID,
                WBS_ID,
                new AssignProjectTaskRequest("STAFF001", null)
        );

        assertThat(response.assignmentId()).isEqualTo(30L);
        assertThat(response.employeeNumber()).isEqualTo("STAFF001");
        assertThat(response.status()).isEqualTo(TaskProgressStatus.TODO);
        assertThat(response.dueDate()).isEqualTo(schedule.getEndDate());
        assertThat(response.assignedHours()).isEqualTo(40.0);
        verify(authorizationService).requireProjectPm(PROJECT_ID);
    }

    @Test
    void replaceFinalAssignmentsValidatesAndStoresEveryLeafTask() {
        Project project = project();
        ProjectWbsTask first = task(project, 21L, 30);
        ProjectWbsTask second = task(project, 22L, 10);
        ProjectSchedule firstSchedule = schedule(project, first);
        ProjectSchedule secondSchedule = schedule(project, second);
        User firstStaff = user("STAFF001");
        User secondStaff = user("STAFF002");

        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(PROJECT_ID))
                .thenReturn(List.of(first, second));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(firstSchedule, secondSchedule));
        when(projectMemberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(
                PROJECT_ID)).thenReturn(List.of(member(project, firstStaff), member(project, secondStaff)));
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of());
        when(authorizationService.currentUser())
                .thenReturn(new AuthenticatedUser("PM001", "PM"));
        when(assignmentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ProjectTaskAssignment> assignments = invocation.getArgument(0);
            for (int index = 0; index < assignments.size(); index++) {
                assignments.get(index).setId(100L + index);
            }
            return assignments;
        });

        var response = service.replaceFinalAssignments(
                PROJECT_ID,
                new SaveFinalTaskAssignmentsRequest(List.of(
                        new SaveFinalTaskAssignmentsRequest.Assignment(
                                first.getId(), "STAFF001", 24.0, null),
                        new SaveFinalTaskAssignmentsRequest.Assignment(
                                second.getId(), "STAFF002", null, secondSchedule.getEndDate())
                ))
        );

        assertThat(response).hasSize(2);
        assertThat(response.getFirst().employeeNumber()).isEqualTo("STAFF001");
        assertThat(response.getFirst().assignedHours()).isEqualTo(24.0);
        assertThat(response.getFirst().assignedBy()).isEqualTo("PM001");
        assertThat(response.getLast().assignedHours()).isEqualTo(10.0);
        assertThat(response).allMatch(item -> item.status() == TaskProgressStatus.TODO);
        verify(authorizationService).requireProjectPm(project);
    }

    @Test
    void replaceFinalAssignmentsRejectsMissingLeafTaskWithoutChangingAssignments() {
        Project project = project();
        ProjectWbsTask first = task(project, 21L, 30);
        ProjectWbsTask second = task(project, 22L, 10);
        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(PROJECT_ID))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.replaceFinalAssignments(
                PROJECT_ID,
                new SaveFinalTaskAssignmentsRequest(List.of(
                        new SaveFinalTaskAssignmentsRequest.Assignment(
                                first.getId(), "STAFF001", 30.0, null)
                ))
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("INCOMPLETE_FINAL_ASSIGNMENTS")
        );
    }

    @Test
    void getAssignmentsReturnsAllAssignmentsForProjectPm() {
        Project project = project();
        ProjectWbsTask task = task(project, WBS_ID, 40);
        ProjectSchedule schedule = schedule(project, task);
        ProjectTaskAssignment assignment = assignment(project, task, "STAFF001", 20);
        assignment.setAssignedHours(32.0);
        assignment.setAssignedBy("PM001");
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(assignment));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(schedule));

        var response = service.getAssignments(PROJECT_ID);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.wbsId()).isEqualTo(WBS_ID);
            assertThat(item.employeeNumber()).isEqualTo("STAFF001");
            assertThat(item.assignedHours()).isEqualTo(32.0);
        });
        verify(authorizationService).requireProjectPm(PROJECT_ID);
    }

    @Test
    void projectProgressUsesEstimatedHoursAsWeight() {
        Project project = project();
        ProjectWbsTask first = task(project, 21L, 30);
        ProjectWbsTask second = task(project, 22L, 10);
        ProjectTaskAssignment firstAssignment = assignment(project, first, "STAFF001", 50);
        ProjectTaskAssignment secondAssignment = assignment(project, second, "STAFF002", 100);
        secondAssignment.setStatus(TaskProgressStatus.COMPLETED);
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(PROJECT_ID))
                .thenReturn(List.of(first, second));
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(firstAssignment, secondAssignment));

        var response = service.getProjectProgress(PROJECT_ID);

        assertThat(response.progressRate()).isEqualTo(63);
        assertThat(response.totalTaskCount()).isEqualTo(2);
        assertThat(response.completedTaskCount()).isEqualTo(1);
        assertThat(response.totalEstimatedHours()).isEqualTo(40);
    }

    @Test
    void staffCannotUpdateAnotherMembersTask() {
        Project project = project();
        ProjectWbsTask task = task(project, WBS_ID, 40);
        ProjectTaskAssignment assignment = assignment(project, task, "STAFF001", 0);
        when(assignmentRepository.findByProjectIdAndWbsTaskId(PROJECT_ID, WBS_ID))
                .thenReturn(Optional.of(assignment));
        when(authorizationService.currentUser())
                .thenReturn(new AuthenticatedUser("STAFF999", "STAFF"));

        assertThatThrownBy(() -> service.updateProgress(
                PROJECT_ID,
                WBS_ID,
                new UpdateTaskProgressRequest(TaskProgressStatus.IN_PROGRESS, 30)
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("TASK_PROGRESS_FORBIDDEN")
        );
    }

    @Test
    void completedTaskRequiresOneHundredPercentProgress() {
        Project project = project();
        ProjectWbsTask task = task(project, WBS_ID, 40);
        ProjectTaskAssignment assignment = assignment(project, task, "STAFF001", 50);
        when(assignmentRepository.findByProjectIdAndWbsTaskId(PROJECT_ID, WBS_ID))
                .thenReturn(Optional.of(assignment));
        when(authorizationService.currentUser())
                .thenReturn(new AuthenticatedUser("STAFF001", "STAFF"));

        assertThatThrownBy(() -> service.updateProgress(
                PROJECT_ID,
                WBS_ID,
                new UpdateTaskProgressRequest(TaskProgressStatus.COMPLETED, 90)
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("INVALID_TASK_PROGRESS")
        );
    }

    @Test
    void searchReturnsMatchingProjectData() {
        Project project = project();
        project.setName("Payment Platform Renewal");
        project.setDescription("Replace the legacy payment platform");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        var response = service.search(PROJECT_ID, "payment", 10);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.type()).isEqualTo("PROJECT");
            assertThat(result.title()).isEqualTo("Payment Platform Renewal");
        });
        verify(authorizationService).requireProjectAccess(PROJECT_ID);
    }

    private Project project() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Project");
        return project;
    }

    private ProjectWbsTask task(Project project, Long id, int hours) {
        ProjectWbsTask task = new ProjectWbsTask();
        task.setId(id);
        task.setProject(project);
        task.setTaskCode("1." + id);
        task.setTaskName("Task " + id);
        task.setDescription("Description");
        task.setEstimatedHours(hours);
        task.setConfirmed(true);
        task.setOrderIndex(id.intValue());
        return task;
    }

    private ProjectSchedule schedule(Project project, ProjectWbsTask task) {
        ProjectSchedule schedule = new ProjectSchedule();
        schedule.setProject(project);
        schedule.setWbsTask(task);
        schedule.setStartDate(LocalDate.now());
        schedule.setEndDate(LocalDate.now().plusDays(5));
        return schedule;
    }

    private ProjectTaskAssignment assignment(
            Project project,
            ProjectWbsTask task,
            String employeeNumber,
            int progressRate
    ) {
        ProjectTaskAssignment assignment = new ProjectTaskAssignment();
        assignment.setProject(project);
        assignment.setWbsTask(task);
        assignment.setEmployeeNumber(employeeNumber);
        assignment.setStatus(TaskProgressStatus.IN_PROGRESS);
        assignment.setProgressRate(progressRate);
        assignment.setDueDate(LocalDate.now().plusDays(5));
        return assignment;
    }

    private User user(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(employeeNumber);
        return user;
    }

    private ProjectMember member(Project project, User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setActive(true);
        return member;
    }
}
