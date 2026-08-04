package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiMemberDelayRequest;
import com.aivle26.aipm.Dto.AiMemberDelayResponse;
import com.aivle26.aipm.Dto.MemberDelayResponse;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberDelayService {
    private final ProjectAuthorizationService authorizationService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final MemberDelayAgentClient agentClient;

    @Transactional(readOnly = true)
    public MemberDelayResponse analyze(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        List<ProjectMember> projectMembers = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        if (projectMembers.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NO_PROJECT_MEMBERS",
                    "Project members have not been registered."
            );
        }

        Map<String, List<ProjectTaskAssignment>> assignmentsByEmployee = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        ProjectTaskAssignment::getEmployeeNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        LocalDate today = LocalDate.now();
        List<AiMemberDelayRequest.AiMemberTaskStatus> members = projectMembers.stream()
                .map(member -> toAiMember(
                        member,
                        assignmentsByEmployee.getOrDefault(
                                member.getUser().getEmployeeNumber(),
                                List.of()
                        ),
                        today
                ))
                .toList();

        AiMemberDelayResponse aiResponse = agentClient.analyze(
                new AiMemberDelayRequest(projectId, members)
        );
        return MemberDelayResponse.from(aiResponse);
    }

    private AiMemberDelayRequest.AiMemberTaskStatus toAiMember(
            ProjectMember member,
            List<ProjectTaskAssignment> assignments,
            LocalDate today
    ) {
        int completed = (int) assignments.stream()
                .filter(assignment -> assignment.getStatus() == TaskProgressStatus.COMPLETED)
                .count();
        int inProgress = (int) assignments.stream()
                .filter(assignment -> assignment.getStatus() == TaskProgressStatus.IN_PROGRESS
                        || assignment.getStatus() == TaskProgressStatus.REVIEW
                        || assignment.getStatus() == TaskProgressStatus.DELAYED)
                .count();
        List<ProjectTaskAssignment> overdueAssignments = assignments.stream()
                .filter(assignment -> isOverdue(assignment, today))
                .toList();
        double averageDelayDays = overdueAssignments.stream()
                .mapToLong(assignment -> Math.max(
                        0,
                        ChronoUnit.DAYS.between(assignment.getDueDate(), today)
                ))
                .average()
                .orElse(0.0);
        int daysSinceLastUpdate = assignments.stream()
                .map(ProjectTaskAssignment::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(LocalDateTime::compareTo)
                .map(updatedAt -> (int) Math.max(
                        0,
                        ChronoUnit.DAYS.between(updatedAt.toLocalDate(), today)
                ))
                .orElse(0);

        return new AiMemberDelayRequest.AiMemberTaskStatus(
                member.getId(),
                member.getUser().getName(),
                assignments.size(),
                completed,
                overdueAssignments.size(),
                inProgress,
                averageDelayDays,
                daysSinceLastUpdate
        );
    }

    private boolean isOverdue(ProjectTaskAssignment assignment, LocalDate today) {
        return assignment.getStatus() == TaskProgressStatus.DELAYED
                || assignment.getStatus() != TaskProgressStatus.COMPLETED
                && assignment.getDueDate() != null
                && assignment.getDueDate().isBefore(today);
    }
}
