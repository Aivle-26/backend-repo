package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.SaveWeeklyScrumRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.WeeklyScrumSubmission;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumSubmissionRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyScrumServiceTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 3);

    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock WeeklyScrumSubmissionRepository submissionRepository;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WeeklyScrumService service;

    @Test
    void staffCreatesOwnWeeklyScrum() {
        Project project = project();
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("E-1", "STAFF"));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(submissionRepository.findByProjectIdAndEmployeeNumberAndWeekStartDate(1L, "E-1", WEEK_START))
                .thenReturn(Optional.empty());
        when(submissionRepository.save(any(WeeklyScrumSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(1L, WEEK_START,
                new SaveWeeklyScrumRequest(" API 구현 ", " 테스트 작성 ", " 없음 "));

        verify(authorizationService).requireProjectAccess(1L);
        assertThat(response.employeeNumber()).isEqualTo("E-1");
        assertThat(response.completedWork()).isEqualTo("API 구현");
        assertThat(response.blockers()).isEqualTo("없음");
    }

    @Test
    void repeatedSubmissionUpdatesExistingRecord() {
        Project project = project();
        WeeklyScrumSubmission existing = new WeeklyScrumSubmission(
                project, "E-1", WEEK_START, "기존 업무", "기존 계획", null);
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("E-1", "STAFF"));
        when(submissionRepository.findByProjectIdAndEmployeeNumberAndWeekStartDate(1L, "E-1", WEEK_START))
                .thenReturn(Optional.of(existing));
        when(submissionRepository.save(existing)).thenReturn(existing);

        var response = service.save(1L, WEEK_START,
                new SaveWeeklyScrumRequest("수정 업무", "수정 계획", null));

        assertThat(response.completedWork()).isEqualTo("수정 업무");
        assertThat(response.plannedWork()).isEqualTo("수정 계획");
    }

    @Test
    void pmGetsMembersWhoDidNotSubmit() {
        when(projectMemberRepository.findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L)).thenReturn(List.of(
                member("E-1"), member("E-2"), member("E-3")));
        when(submissionRepository.findSubmittedEmployeeNumbers(1L, WEEK_START))
                .thenReturn(List.of("E-1"));

        var response = service.getMissingMembers(1L, WEEK_START);

        verify(authorizationService).requireProjectPm(1L);
        assertThat(response.totalMemberCount()).isEqualTo(3);
        assertThat(response.submittedMemberCount()).isEqualTo(1);
        assertThat(response.missingEmployeeNumbers()).containsExactly("E-2", "E-3");
    }

    @Test
    void staffCanOnlyRetrieveOwnSubmission() {
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("E-1", "STAFF"));
        when(submissionRepository.findByProjectIdAndWeekStartDateAndEmployeeNumberOrderByUpdatedAtDesc(
                1L, WEEK_START, "E-1")).thenReturn(List.of());

        service.getByWeek(1L, WEEK_START);

        verify(submissionRepository).findByProjectIdAndWeekStartDateAndEmployeeNumberOrderByUpdatedAtDesc(
                1L, WEEK_START, "E-1");
    }

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setName("AIPM");
        return project;
    }

    private ProjectMember member(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        ProjectMember member = new ProjectMember();
        member.setProject(project());
        member.setUser(user);
        member.setActive(true);
        return member;
    }
}
