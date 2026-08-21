package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiMemberDelayRequest;
import com.aivle26.aipm.Dto.AiMemberDelayResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberDelayServiceTest {
    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectTaskAssignmentRepository assignmentRepository;
    @Mock MemberDelayAgentClient agentClient;
    @InjectMocks MemberDelayService service;

    @Test
    void buildsDelayInputFromActualProjectAssignments() {
        Project project = new Project();
        project.setId(1L);
        User user = new User();
        user.setEmployeeNumber("STAFF001");
        user.setName("Developer");
        ProjectMember member = new ProjectMember();
        member.setId(10L);
        member.setProject(project);
        member.setUser(user);
        member.setActive(true);

        ProjectWbsTask task = new ProjectWbsTask();
        task.setId(20L);
        ProjectTaskAssignment assignment = new ProjectTaskAssignment();
        assignment.setProject(project);
        assignment.setWbsTask(task);
        assignment.setEmployeeNumber("STAFF001");
        assignment.setStatus(TaskProgressStatus.IN_PROGRESS);
        assignment.setDueDate(LocalDate.now().minusDays(3));
        assignment.setUpdatedAt(LocalDateTime.now().minusDays(2));

        when(projectMemberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of(member));
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(1L))
                .thenReturn(List.of(assignment));
        when(agentClient.analyze(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiMemberDelayResponse(1L, 1, 1, List.of()));

        service.analyze(1L);

        verify(authorizationService).requireProjectPm(1L);
        ArgumentCaptor<AiMemberDelayRequest> captor = ArgumentCaptor.forClass(AiMemberDelayRequest.class);
        verify(agentClient).analyze(captor.capture());
        assertThat(captor.getValue().members()).singleElement().satisfies(input -> {
            assertThat(input.memberId()).isEqualTo(10L);
            assertThat(input.memberName()).isEqualTo("Developer");
            assertThat(input.assignedTaskCount()).isEqualTo(1);
            assertThat(input.overdueTaskCount()).isEqualTo(1);
            assertThat(input.inProgressTaskCount()).isEqualTo(1);
            assertThat(input.averageDelayDays()).isGreaterThanOrEqualTo(3.0);
        });
    }
}
