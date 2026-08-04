package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiReassignmentRequest;
import com.aivle26.aipm.Dto.AiReassignmentResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import com.aivle26.aipm.Entity.project.WbsPhase;
import com.aivle26.aipm.Entity.project.WbsSkill;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserSkill;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReassignmentServiceTest {
    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectTaskAssignmentRepository assignmentRepository;
    @Mock UserCapabilityProfileRepository capabilityProfileRepository;
    @Mock ReassignmentAgentClient agentClient;
    @InjectMocks ReassignmentService service;

    @Test
    void buildsCandidatesFromActiveProjectMembers() {
        Project project = new Project();
        project.setId(1L);
        ProjectMember current = member(project, 10L, "STAFF001", "Current", 32.0);
        ProjectMember candidate = member(project, 11L, "STAFF002", "Candidate", 40.0);
        ProjectWbsTask task = new ProjectWbsTask();
        task.setId(20L);
        task.setTaskName("Backend API");
        task.setPhase(WbsPhase.DEVELOPMENT);
        task.setRequiredSkills(new LinkedHashSet<>(Set.of(WbsSkill.BACKEND_DEVELOPMENT)));
        task.setEstimatedHours(40);
        ProjectTaskAssignment assignment = new ProjectTaskAssignment();
        assignment.setProject(project);
        assignment.setWbsTask(task);
        assignment.setEmployeeNumber("STAFF001");
        assignment.setStatus(TaskProgressStatus.IN_PROGRESS);
        assignment.setDueDate(LocalDate.now().plusDays(3));

        when(assignmentRepository.findByProjectIdAndWbsTaskId(1L, 20L))
                .thenReturn(Optional.of(assignment));
        when(projectMemberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of(current, candidate));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(List.of("STAFF001", "STAFF002")))
                .thenReturn(List.of(profile(current.getUser(), "BACKEND", "JAVA"),
                        profile(candidate.getUser(), "BACKEND", "SPRING")));
        when(assignmentRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(1L))
                .thenReturn(List.of(assignment));
        when(agentClient.recommend(any())).thenReturn(new AiReassignmentResponse(
                1L, 20L, true, 80, "HIGH", null, List.of(), List.of()
        ));

        service.recommend(1L, 20L, null);

        verify(authorizationService).requireProjectPm(1L);
        ArgumentCaptor<AiReassignmentRequest> captor = ArgumentCaptor.forClass(AiReassignmentRequest.class);
        verify(agentClient).recommend(captor.capture());
        assertThat(captor.getValue().currentAssignee().memberId()).isEqualTo(10L);
        assertThat(captor.getValue().currentAssignee().memberName()).isEqualTo("Current");
        assertThat(captor.getValue().candidates()).singleElement().satisfies(input -> {
            assertThat(input.memberId()).isEqualTo(11L);
            assertThat(input.memberName()).isEqualTo("Candidate");
            assertThat(input.role()).isEqualTo("BACKEND");
            assertThat(input.skills()).containsExactly("SPRING");
        });
    }

    private ProjectMember member(
            Project project,
            Long id,
            String employeeNumber,
            String name,
            double availableHours
    ) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        ProjectMember member = new ProjectMember();
        member.setId(id);
        member.setProject(project);
        member.setUser(user);
        member.setAvailableHoursPerWeek(availableHours);
        member.setActive(true);
        return member;
    }

    private UserCapabilityProfile profile(User user, String role, String skillCode) {
        UserSkill skill = new UserSkill();
        skill.setSkillCode(skillCode);
        skill.setProficiencyLevel(4);
        skill.setExperienceMonths(24);
        UserCapabilityProfile profile = new UserCapabilityProfile();
        profile.setEmployeeNumber(user.getEmployeeNumber());
        profile.setUser(user);
        profile.setRoles(new LinkedHashSet<>(Set.of(role)));
        profile.setSkills(List.of(skill));
        return profile;
    }
}
