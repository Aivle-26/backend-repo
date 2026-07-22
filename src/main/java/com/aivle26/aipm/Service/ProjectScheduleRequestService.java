package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AgentRequestResult;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectWbsTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectScheduleRequestService {
    private final ProjectRepository projectRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectAgentClient projectAgentClient;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional(readOnly = true)
    public AgentRequestResult requestScheduleGeneration(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        if (!projectWbsTaskRepository.existsByProjectIdAndConfirmedTrue(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed wbs not found");
        }
        return projectAgentClient.requestScheduleGeneration(projectId);
    }
}
