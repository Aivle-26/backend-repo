package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.risk.RiskTeamMemberRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {
    private static final String PM_ROLE = "PM";
    private static final String STAFF_ROLE = "STAFF";

    private final ProjectRepository projectRepository;
    private final RiskTeamMemberRepository riskTeamMemberRepository;

    public void requireCurrentPm(String requestedPmEmployeeNumber) {
        AuthenticatedUser currentUser = currentUser();
        if (!PM_ROLE.equals(currentUser.role())
                || requestedPmEmployeeNumber == null
                || !currentUser.employeeNumber().equals(requestedPmEmployeeNumber.trim())) {
            throw new AccessDeniedException("Access is denied");
        }
    }

    public void requireProjectPm(Long projectId) {
        Project project = requireProject(projectId);
        AuthenticatedUser currentUser = currentUser();
        if (!PM_ROLE.equals(currentUser.role())
                || !project.getPm().getEmployeeNumber().equals(currentUser.employeeNumber())) {
            throw new AccessDeniedException("Access is denied");
        }
    }

    public void requireProjectAccess(Long projectId) {
        Project project = requireProject(projectId);
        AuthenticatedUser currentUser = currentUser();
        if (PM_ROLE.equals(currentUser.role())
                && project.getPm().getEmployeeNumber().equals(currentUser.employeeNumber())) {
            return;
        }
        if (STAFF_ROLE.equals(currentUser.role())
                && riskTeamMemberRepository.existsByProjectIdAndMemberNameAndRoleIgnoreCase(
                        projectId,
                        currentUser.employeeNumber(),
                        STAFF_ROLE
                )) {
            return;
        }
        throw new AccessDeniedException("Access is denied");
    }

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Access is denied");
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }

        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElse("");
        return new AuthenticatedUser(authentication.getName(), role);
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROJECT_NOT_FOUND",
                    "프로젝트를 찾을 수 없습니다."
            );
        }
        return projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));
    }
}
