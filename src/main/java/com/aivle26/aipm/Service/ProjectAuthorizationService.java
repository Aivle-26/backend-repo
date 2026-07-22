package com.aivle26.aipm.Service;

import com.aivle26.aipm.Repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {
    private static final String PM_ROLE = "PM";

    private final ProjectRepository projectRepository;

    public void requireCurrentPm(String requestedPmEmployeeNumber) {
        AuthenticatedUser currentUser = currentUser();
        if (!PM_ROLE.equals(currentUser.role())
                || requestedPmEmployeeNumber == null
                || !currentUser.employeeNumber().equals(requestedPmEmployeeNumber.trim())) {
            throw new AccessDeniedException("Access is denied");
        }
    }

    public void requireProjectPm(Long projectId) {
        AuthenticatedUser currentUser = currentUser();
        if (!PM_ROLE.equals(currentUser.role())
                || projectId == null
                || !projectRepository.existsByIdAndPm_EmployeeNumber(projectId, currentUser.employeeNumber())) {
            throw new AccessDeniedException("Access is denied");
        }
    }

    private AuthenticatedUser currentUser() {
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
}
