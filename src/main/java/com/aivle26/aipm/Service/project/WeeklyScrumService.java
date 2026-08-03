package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.MissingWeeklyScrumMembersResponse;
import com.aivle26.aipm.Dto.project.SaveWeeklyScrumRequest;
import com.aivle26.aipm.Dto.project.WeeklyScrumSubmissionResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.WeeklyScrumSubmission;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumSubmissionRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WeeklyScrumService {

    private static final String PM_ROLE = "PM";
    private static final String STAFF_ROLE = "STAFF";

    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WeeklyScrumSubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WeeklyScrumSubmissionResponse save(Long projectId, LocalDate weekStartDate,
                                              SaveWeeklyScrumRequest request) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectAccess(projectId);
        AuthenticatedUser currentUser = authorizationService.currentUser();
        if (!STAFF_ROLE.equals(currentUser.role())) {
            throw new AccessDeniedException("Access is denied");
        }

        WeeklyScrumSubmission submission = submissionRepository
                .findByProjectIdAndEmployeeNumberAndWeekStartDate(
                        projectId, currentUser.employeeNumber(), weekStartDate)
                .orElseGet(() -> new WeeklyScrumSubmission(
                        requireProject(projectId), currentUser.employeeNumber(), weekStartDate,
                        request.completedWork().trim(), request.plannedWork().trim(), normalizeNullable(request.blockers())));
        submission.update(
                request.completedWork().trim(),
                request.plannedWork().trim(),
                normalizeNullable(request.blockers()),
                writeDetails(request.details())
        );
        return toResponse(submissionRepository.save(submission));
    }

    @Transactional(readOnly = true)
    public List<WeeklyScrumSubmissionResponse> getByWeek(Long projectId, LocalDate weekStartDate) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectAccess(projectId);
        AuthenticatedUser currentUser = authorizationService.currentUser();
        List<WeeklyScrumSubmission> submissions = PM_ROLE.equals(currentUser.role())
                ? submissionRepository.findByProjectIdAndWeekStartDateOrderByUpdatedAtDescEmployeeNumberAsc(
                        projectId, weekStartDate)
                : submissionRepository.findByProjectIdAndWeekStartDateAndEmployeeNumberOrderByUpdatedAtDesc(
                        projectId, weekStartDate, currentUser.employeeNumber());
        return submissions.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MissingWeeklyScrumMembersResponse getMissingMembers(Long projectId, LocalDate weekStartDate) {
        validateMonday(weekStartDate);
        authorizationService.requireProjectPm(projectId);

        LinkedHashSet<String> allMembers = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId)
                .stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> submitted = Set.copyOf(
                submissionRepository.findSubmittedEmployeeNumbers(projectId, weekStartDate));
        List<String> missing = allMembers.stream()
                .filter(employeeNumber -> !submitted.contains(employeeNumber))
                .toList();
        int submittedMemberCount = (int) allMembers.stream().filter(submitted::contains).count();
        return new MissingWeeklyScrumMembersResponse(
                projectId, weekStartDate, allMembers.size(), submittedMemberCount, missing);
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }

    private void validateMonday(LocalDate weekStartDate) {
        if (weekStartDate == null || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_WEEK_START_DATE",
                    "weekStartDate는 월요일이어야 합니다.");
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writeDetails(SaveWeeklyScrumRequest.Details details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "WEEKLY_SCRUM_SERIALIZATION_FAILED",
                    "주간 스크럼 상세 정보를 저장하지 못했습니다.",
                    exception
            );
        }
    }

    private WeeklyScrumSubmissionResponse toResponse(WeeklyScrumSubmission submission) {
        return WeeklyScrumSubmissionResponse.from(submission, readDetails(submission.getDetailsJson()));
    }

    private SaveWeeklyScrumRequest.Details readDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(detailsJson, SaveWeeklyScrumRequest.Details.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_WEEKLY_SCRUM_DETAILS",
                    "저장된 주간 스크럼 상세 정보 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }
}
