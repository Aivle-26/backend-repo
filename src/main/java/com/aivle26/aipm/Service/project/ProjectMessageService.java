package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateFeedbackRequest;
import com.aivle26.aipm.Dto.project.CreateNoticeRequest;
import com.aivle26.aipm.Dto.project.CreateScrumRequestsRequest;
import com.aivle26.aipm.Dto.project.ProjectMessageResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMessage;
import com.aivle26.aipm.Entity.project.ProjectMessageType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMessageRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMessageService {

    private static final String DEFAULT_SCRUM_REQUEST_MESSAGE = "이번 주 주간 스크럼을 작성해 주세요.";

    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMessageRepository messageRepository;

    @Transactional
    public ProjectMessageResponse createNotice(Long projectId, CreateNoticeRequest request) {
        Project project = requirePmProject(projectId);
        AuthenticatedUser sender = authorizationService.currentUser();
        return ProjectMessageResponse.from(messageRepository.save(ProjectMessage.notice(
                project, sender.employeeNumber(), request.title().trim(), request.content().trim())));
    }

    @Transactional(readOnly = true)
    public List<ProjectMessageResponse> getNotices(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        return map(messageRepository.findByProjectIdAndTypeOrderByCreatedAtDescIdDesc(
                projectId, ProjectMessageType.NOTICE));
    }

    @Transactional
    public ProjectMessageResponse createFeedback(Long projectId, CreateFeedbackRequest request) {
        Project project = requirePmProject(projectId);
        String recipient = normalizeAndValidateMember(projectId, request.recipientEmployeeNumber());
        AuthenticatedUser sender = authorizationService.currentUser();
        return ProjectMessageResponse.from(messageRepository.save(ProjectMessage.feedback(
                project, sender.employeeNumber(), recipient,
                request.title().trim(), request.content().trim())));
    }

    @Transactional(readOnly = true)
    public List<ProjectMessageResponse> getFeedbacks(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        AuthenticatedUser currentUser = authorizationService.currentUser();
        if ("PM".equals(currentUser.role())) {
            return map(messageRepository.findByProjectIdAndTypeOrderByCreatedAtDescIdDesc(
                    projectId, ProjectMessageType.FEEDBACK));
        }
        return map(messageRepository.findByProjectIdAndTypeAndRecipientEmployeeNumberOrderByCreatedAtDescIdDesc(
                projectId, ProjectMessageType.FEEDBACK, currentUser.employeeNumber()));
    }

    @Transactional
    public List<ProjectMessageResponse> createScrumRequests(Long projectId, CreateScrumRequestsRequest request) {
        Project project = requirePmProject(projectId);
        validateMonday(request.weekStartDate());
        AuthenticatedUser sender = authorizationService.currentUser();
        String content = request.message() == null || request.message().isBlank()
                ? DEFAULT_SCRUM_REQUEST_MESSAGE
                : request.message().trim();

        LinkedHashSet<String> recipients = request.recipientEmployeeNumbers().stream()
                .map(value -> normalizeAndValidateMember(projectId, value))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (String recipient : recipients) {
            if (messageRepository.existsByProjectIdAndTypeAndRecipientEmployeeNumberAndTargetWeekStart(
                    projectId, ProjectMessageType.SCRUM_REQUEST, recipient, request.weekStartDate())) {
                throw new ApiException(HttpStatus.CONFLICT, "SCRUM_REQUEST_ALREADY_EXISTS",
                        "해당 팀원에게 같은 주차의 스크럼 작성 요청을 이미 보냈습니다. employeeNumber=" + recipient);
            }
        }

        List<ProjectMessage> messages = recipients.stream()
                .map(recipient -> ProjectMessage.scrumRequest(project, sender.employeeNumber(), recipient,
                        request.weekStartDate(), content))
                .toList();
        return map(messageRepository.saveAll(messages));
    }

    @Transactional(readOnly = true)
    public List<ProjectMessageResponse> getScrumRequests(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        AuthenticatedUser currentUser = authorizationService.currentUser();
        if ("PM".equals(currentUser.role())) {
            return map(messageRepository.findByProjectIdAndTypeOrderByCreatedAtDescIdDesc(
                    projectId, ProjectMessageType.SCRUM_REQUEST));
        }
        return map(messageRepository.findByProjectIdAndTypeAndRecipientEmployeeNumberOrderByCreatedAtDescIdDesc(
                projectId, ProjectMessageType.SCRUM_REQUEST, currentUser.employeeNumber()));
    }

    private Project requirePmProject(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        return projectRepository.findWithPmById(projectId).orElseThrow();
    }

    private String normalizeAndValidateMember(Long projectId, String employeeNumber) {
        String normalized = employeeNumber.trim();
        if (!projectMemberRepository.existsByProjectIdAndUser_EmployeeNumberAndActiveTrue(
                projectId, normalized)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROJECT_TEAM_MEMBER_NOT_FOUND",
                    "프로젝트 팀원을 찾을 수 없습니다. employeeNumber=" + normalized);
        }
        return normalized;
    }

    private void validateMonday(java.time.LocalDate weekStartDate) {
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_WEEK_START_DATE",
                    "weekStartDate는 월요일이어야 합니다.");
        }
    }

    private List<ProjectMessageResponse> map(List<ProjectMessage> messages) {
        return messages.stream().map(ProjectMessageResponse::from).toList();
    }
}
