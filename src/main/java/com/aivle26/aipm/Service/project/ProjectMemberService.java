package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.ProjectMemberResponse;
import com.aivle26.aipm.Dto.project.SaveProjectMembersRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {
    private static final String STAFF_ROLE = "STAFF";
    private static final double DEFAULT_AVAILABLE_HOURS_PER_WEEK = 32.0;

    private final ProjectAuthorizationService authorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;
    private final OrganizationChartAutoGenerationService organizationChartAutoGenerationService;

    @Transactional
    public List<ProjectMemberResponse> replaceMembers(
            Long projectId,
            SaveProjectMembersRequest request
    ) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        authorizationService.requireProjectPm(project);

        Map<String, Double> requestedHours = normalizeMembers(request.members());
        Map<String, User> usersByEmployeeNumber = loadAssignableUsers(project, requestedHours.keySet());
        List<ProjectMember> existingMembers = projectMemberRepository
                .findByProjectIdOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        Map<String, ProjectMember> existingByEmployeeNumber = existingMembers.stream()
                .collect(Collectors.toMap(
                        member -> member.getUser().getEmployeeNumber(),
                        Function.identity()
                ));

        validateRemovals(projectId, existingMembers, requestedHours.keySet());
        String selectedBy = authorizationService.currentUser().employeeNumber();
        List<ProjectMember> changed = new ArrayList<>();

        for (ProjectMember existing : existingMembers) {
            if (!requestedHours.containsKey(existing.getUser().getEmployeeNumber())) {
                existing.setActive(false);
                changed.add(existing);
            }
        }
        for (Map.Entry<String, Double> entry : requestedHours.entrySet()) {
            ProjectMember member = existingByEmployeeNumber.get(entry.getKey());
            if (member == null) {
                member = new ProjectMember();
                member.setProject(project);
                member.setUser(usersByEmployeeNumber.get(entry.getKey()));
            }
            member.setAvailableHoursPerWeek(entry.getValue());
            member.setActive(true);
            member.setSelectedBy(selectedBy);
            changed.add(member);
        }
        projectMemberRepository.saveAll(changed);
        organizationChartAutoGenerationService.scheduleAfterCommit(projectId);
        return getMembers(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(Long projectId) {
        authorizationService.requireProjectAccess(projectId);
        Project project = projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        List<ProjectMember> members = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        List<String> profileEmployeeNumbers = members.stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(Collectors.toCollection(ArrayList::new));
        if (!profileEmployeeNumbers.contains(project.getPm().getEmployeeNumber())) {
            profileEmployeeNumbers.add(project.getPm().getEmployeeNumber());
        }
        Map<String, UserCapabilityProfile> profiles = loadProfiles(profileEmployeeNumbers);
        List<ProjectMemberResponse> responses = members.stream()
                .map(member -> toResponse(
                        member,
                        profiles.get(member.getUser().getEmployeeNumber())
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        boolean pmAlreadyIncluded = members.stream().anyMatch(member ->
                member.getUser().getEmployeeNumber().equals(project.getPm().getEmployeeNumber()));
        if (!pmAlreadyIncluded) {
            responses.add(toProjectManagerResponse(
                    project,
                    profiles.get(project.getPm().getEmployeeNumber())
            ));
        }
        return responses.stream()
                .sorted(java.util.Comparator.comparing(ProjectMemberResponse::name)
                        .thenComparing(ProjectMemberResponse::employeeNumber))
                .toList();
    }

    private Map<String, Double> normalizeMembers(List<SaveProjectMembersRequest.Member> members) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (SaveProjectMembersRequest.Member member : members) {
            String employeeNumber = member.employeeNumber().trim();
            double availableHours = member.availableHoursPerWeek() == null
                    ? DEFAULT_AVAILABLE_HOURS_PER_WEEK
                    : member.availableHoursPerWeek();
            if (normalized.putIfAbsent(employeeNumber, availableHours) != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_PROJECT_MEMBER",
                        "Duplicate project member. employeeNumber=" + employeeNumber
                );
            }
        }
        return normalized;
    }

    private Map<String, User> loadAssignableUsers(Project project, Set<String> employeeNumbers) {
        if (employeeNumbers.isEmpty()) {
            return Map.of();
        }
        Set<String> staffEmployeeNumbers = new LinkedHashSet<>(employeeNumbers);
        staffEmployeeNumbers.remove(project.getPm().getEmployeeNumber());
        Map<String, User> users = userRepository.findAllByEmployeeNumberInAndRoleAndStatus(
                        staffEmployeeNumbers,
                        STAFF_ROLE,
                        UserStatus.ACTIVE
                ).stream()
                .collect(Collectors.toMap(User::getEmployeeNumber, Function.identity()));
        if (employeeNumbers.contains(project.getPm().getEmployeeNumber())
                && project.getPm().getStatus() == UserStatus.ACTIVE) {
            users.put(project.getPm().getEmployeeNumber(), project.getPm());
        }
        LinkedHashSet<String> missing = new LinkedHashSet<>(employeeNumbers);
        missing.removeAll(users.keySet());
        if (!missing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "ACTIVE_STAFF_NOT_FOUND",
                    "Active staff members were not found. employeeNumbers=" + missing
            );
        }
        return users;
    }

    private void validateRemovals(
            Long projectId,
            List<ProjectMember> existingMembers,
            Set<String> requestedEmployeeNumbers
    ) {
        List<String> assignedMembersToRemove = existingMembers.stream()
                .filter(ProjectMember::isActive)
                .map(member -> member.getUser().getEmployeeNumber())
                .filter(employeeNumber -> !requestedEmployeeNumbers.contains(employeeNumber))
                .filter(employeeNumber -> assignmentRepository
                        .existsByProjectIdAndEmployeeNumber(projectId, employeeNumber))
                .toList();
        if (!assignedMembersToRemove.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROJECT_MEMBER_HAS_ASSIGNMENTS",
                    "Assigned project members cannot be removed. employeeNumbers=" + assignedMembersToRemove
            );
        }
    }

    private Map<String, UserCapabilityProfile> loadProfiles(List<String> employeeNumbers) {
        if (employeeNumbers.isEmpty()) {
            return Map.of();
        }
        return capabilityProfileRepository.findAllByEmployeeNumberIn(
                        employeeNumbers
                ).stream()
                .collect(Collectors.toMap(
                        UserCapabilityProfile::getEmployeeNumber,
                        Function.identity()
                ));
    }

    private ProjectMemberResponse toProjectManagerResponse(
            Project project,
            UserCapabilityProfile profile
    ) {
        User pm = project.getPm();
        List<String> roles = profile == null || profile.getRoles().isEmpty()
                ? List.of("PM")
                : profile.getRoles().stream().sorted().toList();
        List<ProjectMemberResponse.Skill> skills = profile == null
                ? List.of()
                : profile.getSkills().stream()
                .map(skill -> new ProjectMemberResponse.Skill(
                        skill.getSkillCode(),
                        skill.getProficiencyLevel(),
                        skill.getExperienceMonths()
                ))
                .toList();
        return new ProjectMemberResponse(
                null,
                project.getId(),
                pm.getEmployeeNumber(),
                pm.getName(),
                pm.getEmail(),
                DEFAULT_AVAILABLE_HOURS_PER_WEEK,
                pm.getEmployeeNumber(),
                project.getCreatedAt(),
                roles,
                skills
        );
    }

    private ProjectMemberResponse toResponse(
            ProjectMember member,
            UserCapabilityProfile profile
    ) {
        List<String> roles = profile == null
                ? ("PM".equals(userRole(member)) ? List.of("PM") : List.of())
                : profile.getRoles().stream().sorted().toList();
        List<ProjectMemberResponse.Skill> skills = profile == null
                ? List.of()
                : profile.getSkills().stream()
                .map(skill -> new ProjectMemberResponse.Skill(
                        skill.getSkillCode(),
                        skill.getProficiencyLevel(),
                        skill.getExperienceMonths()
                ))
                .toList();
        User user = member.getUser();
        return new ProjectMemberResponse(
                member.getId(),
                member.getProject().getId(),
                user.getEmployeeNumber(),
                user.getName(),
                user.getEmail(),
                member.getAvailableHoursPerWeek(),
                member.getSelectedBy(),
                member.getJoinedAt(),
                roles,
                skills
        );
    }

    private String userRole(ProjectMember member) {
        return member.getUser().getRole();
    }
}
