package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectCostEstimate;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectLifecycleServiceTest {
    private static final Long PROJECT_ID = 10L;

    @Mock private ProjectAuthorizationService authorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectRequirementRepository requirementRepository;
    @Mock private ProjectWbsTaskRepository wbsTaskRepository;
    @Mock private ProjectScheduleRepository scheduleRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private ProjectTaskAssignmentRepository assignmentRepository;
    @Mock private ProjectCostEstimateRepository costEstimateRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ProjectLifecycleService service;

    @Test
    void getDetailReturnsProjectAndReadiness() {
        Project project = project();
        when(projectRepository.findWithPmById(PROJECT_ID)).thenReturn(Optional.of(project));
        stubEmptyReadiness();

        var response = service.getDetail(PROJECT_ID);

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.name()).isEqualTo("Project");
        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(response.finalizationReadiness().ready()).isFalse();
        verify(authorizationService).requireProjectAccess(PROJECT_ID);
    }

    @Test
    void finalizeProjectActivatesReadyDraft() {
        Project project = project();
        ProjectWbsTask task = task(project);
        ProjectSchedule schedule = new ProjectSchedule();
        schedule.setProject(project);
        schedule.setWbsTask(task);
        schedule.setConfirmed(true);
        ProjectTaskAssignment assignment = new ProjectTaskAssignment();
        assignment.setProject(project);
        assignment.setWbsTask(task);
        assignment.setEmployeeNumber("STAFF001");
        User staff = new User();
        staff.setEmployeeNumber("STAFF001");
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(staff);
        member.setActive(true);
        ProjectCostEstimate cost = new ProjectCostEstimate();
        cost.setConfirmed(true);

        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(requirementRepository.findByProjectIdAndStatus(PROJECT_ID, RequirementStatus.CONFIRMED))
                .thenReturn(List.of(new ProjectRequirement()));
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(PROJECT_ID)).thenReturn(List.of(task));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(schedule));
        when(memberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of(member));
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of(assignment));
        when(costEstimateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(cost));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        var response = service.finalizeProject(PROJECT_ID);

        assertThat(response.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(response.finalizationReadiness().ready()).isTrue();
        verify(authorizationService).requireProjectPm(project);
    }

    @Test
    void finalizeProjectRejectsIncompleteDraft() {
        Project project = project();
        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        stubEmptyReadiness();

        assertThatThrownBy(() -> service.finalizeProject(PROJECT_ID))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PROJECT_NOT_READY")
                );
        verify(projectRepository, never()).saveAndFlush(any());
    }

    private void stubEmptyReadiness() {
        when(requirementRepository.findByProjectIdAndStatus(PROJECT_ID, RequirementStatus.CONFIRMED))
                .thenReturn(List.of());
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(PROJECT_ID)).thenReturn(List.of());
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of());
        when(memberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of());
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(PROJECT_ID))
                .thenReturn(List.of());
        when(costEstimateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    }

    private Project project() {
        User pm = new User();
        pm.setEmployeeNumber("PM001");
        pm.setName("Project Manager");
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Project");
        project.setDescription("Description");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 8, 1));
        project.setPlannedEndDate(LocalDate.of(2026, 8, 31));
        return project;
    }

    private ProjectWbsTask task(Project project) {
        ProjectWbsTask task = new ProjectWbsTask();
        task.setId(20L);
        task.setProject(project);
        task.setConfirmed(true);
        return task;
    }
}
