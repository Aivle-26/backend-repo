package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateFeedbackRequest;
import com.aivle26.aipm.Dto.project.CreateNoticeRequest;
import com.aivle26.aipm.Dto.project.CreateScrumRequestsRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMessage;
import com.aivle26.aipm.Entity.project.ProjectMessageType;
import com.aivle26.aipm.Repository.project.ProjectMessageRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.risk.RiskTeamMemberRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMessageServiceTest {

    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectRepository projectRepository;
    @Mock RiskTeamMemberRepository teamMemberRepository;
    @Mock ProjectMessageRepository messageRepository;
    @InjectMocks ProjectMessageService service;

    @Test
    void pmCreatesBroadcastNotice() {
        Project project = project();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("PM-1", "PM"));
        when(messageRepository.save(any(ProjectMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createNotice(1L, new CreateNoticeRequest(" 배포 안내 ", " 오늘 배포합니다. "));

        verify(authorizationService).requireProjectPm(1L);
        assertThat(result.type()).isEqualTo(ProjectMessageType.NOTICE);
        assertThat(result.recipientEmployeeNumber()).isNull();
        assertThat(result.title()).isEqualTo("배포 안내");
    }

    @Test
    void pmCreatesFeedbackOnlyForProjectMember() {
        Project project = project();
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("PM-1", "PM"));
        when(teamMemberRepository.existsByProjectIdAndMemberNameAndRoleIgnoreCase(1L, "E-1", "STAFF"))
                .thenReturn(true);
        when(messageRepository.save(any(ProjectMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createFeedback(1L,
                new CreateFeedbackRequest(" E-1 ", "코드 리뷰", "테스트를 보강해 주세요."));

        assertThat(result.type()).isEqualTo(ProjectMessageType.FEEDBACK);
        assertThat(result.recipientEmployeeNumber()).isEqualTo("E-1");
    }

    @Test
    void staffRetrievesOnlyOwnFeedback() {
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("E-1", "STAFF"));
        when(messageRepository.findByProjectIdAndTypeAndRecipientEmployeeNumberOrderByCreatedAtDescIdDesc(
                1L, ProjectMessageType.FEEDBACK, "E-1")).thenReturn(List.of());

        service.getFeedbacks(1L);

        verify(authorizationService).requireProjectAccess(1L);
        verify(messageRepository).findByProjectIdAndTypeAndRecipientEmployeeNumberOrderByCreatedAtDescIdDesc(
                1L, ProjectMessageType.FEEDBACK, "E-1");
    }

    @Test
    void createsOneScrumRequestPerUniqueRecipient() {
        Project project = project();
        LocalDate monday = LocalDate.of(2026, 8, 3);
        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
        when(authorizationService.currentUser()).thenReturn(new AuthenticatedUser("PM-1", "PM"));
        when(teamMemberRepository.existsByProjectIdAndMemberNameAndRoleIgnoreCase(
                1L, "E-1", "STAFF")).thenReturn(true);
        when(messageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createScrumRequests(1L,
                new CreateScrumRequestsRequest(monday, List.of("E-1", " E-1 "), null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetWeekStart()).isEqualTo(monday);
        assertThat(result.get(0).content()).isEqualTo("이번 주 주간 스크럼을 작성해 주세요.");
    }

    private Project project() {
        Project project = new Project();
        project.setId(1L);
        project.setName("AIPM");
        return project;
    }
}
