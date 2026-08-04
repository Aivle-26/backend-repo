package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiReassignmentRequest;
import com.aivle26.aipm.Dto.AiReassignmentResponse;
import com.aivle26.aipm.Dto.ReassignmentRequest;
import com.aivle26.aipm.Dto.ReassignmentResponse;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReassignmentService {
    private static final String DEFAULT_PROFESSIONAL_ROLE = "STAFF";

    private final ProjectAuthorizationService authorizationService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;
    private final ReassignmentAgentClient agentClient;

    @Transactional(readOnly = true)
    public ReassignmentResponse recommend(
            Long projectId,
            Long taskId,
            ReassignmentRequest request
    ) {
        authorizationService.requireProjectPm(projectId);
        ProjectTaskAssignment currentAssignment = assignmentRepository
                .findByProjectIdAndWbsTaskId(projectId, taskId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TASK_ASSIGNMENT_NOT_FOUND",
                        "Task assignment was not found."
                ));
        List<ProjectMember> members = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        ProjectMember currentMember = members.stream()
                .filter(member -> member.getUser().getEmployeeNumber()
                        .equals(currentAssignment.getEmployeeNumber()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "ASSIGNEE_NOT_PROJECT_MEMBER",
                        "The current assignee is not an active project member."
                ));
        List<ProjectMember> candidates = members.stream()
                .filter(member -> !member.getId().equals(currentMember.getId()))
                .toList();
        if (candidates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "NO_REASSIGNMENT_CANDIDATES",
                    "No active project members are available for reassignment."
            );
        }

        Map<String, UserCapabilityProfile> profiles = loadProfiles(members);
        Map<String, List<ProjectTaskAssignment>> assignmentsByEmployee = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        ProjectTaskAssignment::getEmployeeNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        ProjectWbsTask task = currentAssignment.getWbsTask();
        AiReassignmentRequest aiRequest = new AiReassignmentRequest(
                projectId,
                task.getId(),
                resolveTaskName(request, task),
                resolveRequiredRole(request, task),
                resolveRequiredSkills(request, task),
                toCurrentAssignee(
                        currentMember,
                        profiles.get(currentMember.getUser().getEmployeeNumber()),
                        assignmentsByEmployee.getOrDefault(
                                currentMember.getUser().getEmployeeNumber(),
                                List.of()
                        )
                ),
                candidates.stream()
                        .map(member -> toCandidate(
                                member,
                                profiles.get(member.getUser().getEmployeeNumber()),
                                assignmentsByEmployee.getOrDefault(
                                        member.getUser().getEmployeeNumber(),
                                        List.of()
                                )
                        ))
                        .toList()
        );
        AiReassignmentResponse aiResponse = agentClient.recommend(aiRequest);
        return ReassignmentResponse.from(aiResponse);
    }

    private Map<String, UserCapabilityProfile> loadProfiles(List<ProjectMember> members) {
        return capabilityProfileRepository.findAllByEmployeeNumberIn(
                        members.stream().map(member -> member.getUser().getEmployeeNumber()).toList()
                ).stream()
                .collect(Collectors.toMap(
                        UserCapabilityProfile::getEmployeeNumber,
                        Function.identity()
                ));
    }

    private AiReassignmentRequest.AiCurrentAssignee toCurrentAssignee(
            ProjectMember member,
            UserCapabilityProfile profile,
            List<ProjectTaskAssignment> assignments
    ) {
        return new AiReassignmentRequest.AiCurrentAssignee(
                member.getId(),
                member.getUser().getName(),
                skills(profile),
                workloadRate(member, assignments),
                overdueCount(assignments)
        );
    }

    private AiReassignmentRequest.AiCandidateMember toCandidate(
            ProjectMember member,
            UserCapabilityProfile profile,
            List<ProjectTaskAssignment> assignments
    ) {
        return new AiReassignmentRequest.AiCandidateMember(
                member.getId(),
                member.getUser().getName(),
                professionalRole(profile),
                skills(profile),
                workloadRate(member, assignments),
                overdueCount(assignments)
        );
    }

    private List<String> skills(UserCapabilityProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return profile.getSkills().stream()
                .map(skill -> skill.getSkillCode().trim())
                .filter(skill -> !skill.isBlank())
                .distinct()
                .toList();
    }

    private String professionalRole(UserCapabilityProfile profile) {
        if (profile == null || profile.getRoles().isEmpty()) {
            return DEFAULT_PROFESSIONAL_ROLE;
        }
        return profile.getRoles().stream().sorted().findFirst()
                .orElse(DEFAULT_PROFESSIONAL_ROLE);
    }

    private double workloadRate(
            ProjectMember member,
            List<ProjectTaskAssignment> assignments
    ) {
        double availableHours = member.getAvailableHoursPerWeek();
        if (availableHours <= 0) {
            return assignments.isEmpty() ? 0.0 : 100.0;
        }
        int assignedHours = assignments.stream()
                .filter(assignment -> assignment.getStatus() != TaskProgressStatus.COMPLETED)
                .mapToInt(assignment -> Math.max(1, assignment.getWbsTask().getEstimatedHours()))
                .sum();
        return Math.min(100.0, assignedHours * 100.0 / availableHours);
    }

    private int overdueCount(List<ProjectTaskAssignment> assignments) {
        LocalDate today = LocalDate.now();
        return (int) assignments.stream()
                .filter(assignment -> assignment.getStatus() == TaskProgressStatus.DELAYED
                        || assignment.getStatus() != TaskProgressStatus.COMPLETED
                        && assignment.getDueDate() != null
                        && assignment.getDueDate().isBefore(today))
                .count();
    }

    private String resolveTaskName(ReassignmentRequest request, ProjectWbsTask task) {
        return request != null && request.taskName() != null && !request.taskName().isBlank()
                ? request.taskName().trim()
                : task.getTaskName();
    }

    private String resolveRequiredRole(ReassignmentRequest request, ProjectWbsTask task) {
        return request != null && request.requiredRole() != null && !request.requiredRole().isBlank()
                ? request.requiredRole().trim()
                : task.getPhase().name();
    }

    private List<String> resolveRequiredSkills(ReassignmentRequest request, ProjectWbsTask task) {
        if (request != null && request.requiredSkills() != null && !request.requiredSkills().isEmpty()) {
            return request.requiredSkills().stream()
                    .filter(skill -> skill != null && !skill.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
        return task.getRequiredSkills().stream().map(Enum::name).sorted().toList();
    }
}
