package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.SaveProjectMembersRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {
    private static final Long PROJECT_ID = 1L;

    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectTaskAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock UserCapabilityProfileRepository capabilityProfileRepository;
    @InjectMocks ProjectMemberService service;

    @Test
    void replacesMembersAndAppliesDefaultAvailability() {
        Project project = project();
        User staff = staff("STAFF001");
        ProjectMember existing = member(project, staff, false);
        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.findWithPmById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(userRepository.findAllByEmployeeNumberInAndRoleAndStatus(
                Set.of("STAFF001"), "STAFF", UserStatus.ACTIVE)).thenReturn(List.of(staff));
        when(projectMemberRepository.findByProjectIdOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of(existing));
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("PM001", "PM"));
        when(projectMemberRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of(existing));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(List.of("STAFF001", "PM001")))
                .thenReturn(List.of());

        var response = service.replaceMembers(
                PROJECT_ID,
                new SaveProjectMembersRequest(List.of(
                        new SaveProjectMembersRequest.Member(" STAFF001 ", null)
                ))
        );

        verify(authorizationService).requireProjectPm(project);
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getAvailableHoursPerWeek()).isEqualTo(32.0);
        assertThat(existing.getSelectedBy()).isEqualTo("PM001");
        assertThat(response).filteredOn(member -> member.employeeNumber().equals("STAFF001"))
                .singleElement().satisfies(member -> {
            assertThat(member.employeeNumber()).isEqualTo("STAFF001");
            assertThat(member.availableHoursPerWeek()).isEqualTo(32.0);
        });
        assertThat(response).anySatisfy(member -> {
            assertThat(member.employeeNumber()).isEqualTo("PM001");
            assertThat(member.roles()).contains("PM");
        });
    }

    @Test
    void projectMemberCandidatesAlwaysIncludeProjectManager() {
        Project project = project();
        when(projectRepository.findWithPmById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of());
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(List.of("PM001")))
                .thenReturn(List.of());

        var response = service.getMembers(PROJECT_ID);

        assertThat(response).singleElement().satisfies(member -> {
            assertThat(member.employeeNumber()).isEqualTo("PM001");
            assertThat(member.name()).isEqualTo("Project PM");
            assertThat(member.roles()).containsExactly("PM");
        });
    }

    @Test
    void rejectsDuplicateMembersBeforeSaving() {
        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project()));

        assertThatThrownBy(() -> service.replaceMembers(
                PROJECT_ID,
                new SaveProjectMembersRequest(List.of(
                        new SaveProjectMembersRequest.Member("STAFF001", 32.0),
                        new SaveProjectMembersRequest.Member(" STAFF001 ", 24.0)
                ))
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("DUPLICATE_PROJECT_MEMBER")
        );
    }

    @Test
    void preventsRemovingMemberWithAssignments() {
        Project project = project();
        ProjectMember existing = member(project, staff("STAFF001"), true);
        when(projectRepository.findForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdOrderByUser_NameAscUser_EmployeeNumberAsc(PROJECT_ID))
                .thenReturn(List.of(existing));
        when(assignmentRepository.existsByProjectIdAndEmployeeNumber(PROJECT_ID, "STAFF001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.replaceMembers(
                PROJECT_ID,
                new SaveProjectMembersRequest(List.of())
        )).isInstanceOfSatisfying(
                ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("PROJECT_MEMBER_HAS_ASSIGNMENTS")
        );
    }

    private Project project() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Project");
        User pm = new User();
        pm.setEmployeeNumber("PM001");
        pm.setName("Project PM");
        pm.setEmail("pm@example.com");
        pm.setRole("PM");
        pm.setStatus(UserStatus.ACTIVE);
        project.setPm(pm);
        return project;
    }

    private User staff(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Staff");
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setRole("STAFF");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private ProjectMember member(Project project, User user, boolean active) {
        ProjectMember member = new ProjectMember();
        member.setId(10L);
        member.setProject(project);
        member.setUser(user);
        member.setAvailableHoursPerWeek(24.0);
        member.setActive(active);
        member.setSelectedBy("PM001");
        return member;
    }
}
