package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AgentRequestResult;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectDocumentAnalysisRequestService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectAgentClient projectAgentClient;

    @Transactional(readOnly = true)
    public AgentRequestResult requestAnalysis(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "project not found");
        }
        if (!projectDocumentRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }
        return projectAgentClient.requestDocumentAnalysis(projectId);
    }
}
