package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AgentRequestResult;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectWbsRequestService {
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectAgentClient projectAgentClient;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional(readOnly = true)
    public AgentRequestResult requestWbsGeneration(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (!projectRequirementRepository.existsByProjectIdAndStatus(projectId, com.aivle26.aipm.Entity.RequirementStatus.CONFIRMED)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed requirement not found");
        }
        return projectAgentClient.requestWbsGeneration(projectId);
    }
}
