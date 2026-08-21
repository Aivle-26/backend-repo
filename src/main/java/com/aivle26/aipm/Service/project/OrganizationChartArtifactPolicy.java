package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationChartArtifactPolicy {

    public static final ProjectArtifactType TYPE = ProjectArtifactType.ORGANIZATION_CHART;
    public static final String NAME = "조직도";
    public static final String INITIAL_VERSION = "1.0";

    private final ProjectRepository projectRepository;
    private final ProjectRequiredArtifactRepository requiredArtifactRepository;

    @Transactional
    public ProjectRequiredArtifact ensureForProject(Long projectId) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        return ensure(project);
    }

    public ProjectRequiredArtifact ensure(Project project) {
        return requiredArtifactRepository
                .findByProjectIdAndArtifactTypeAndArtifactName(
                        project.getId(),
                        TYPE,
                        NAME
                )
                .orElseGet(() -> requiredArtifactRepository.save(
                        newRequiredArtifact(project)
                ));
    }

    public ProjectRequiredArtifact newRequiredArtifact(Project project) {
        ProjectRequiredArtifact requiredArtifact = new ProjectRequiredArtifact();
        requiredArtifact.setProject(project);
        requiredArtifact.setArtifactType(TYPE);
        requiredArtifact.setArtifactName(NAME);
        requiredArtifact.setRequiredVersion(INITIAL_VERSION);
        return requiredArtifact;
    }
}
