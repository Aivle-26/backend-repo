package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.ProjectSummaryResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectPmResolver projectPmResolver;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    public CreateProjectDraftResponse createProjectDraft(CreateProjectDraftRequest request) {
        projectAuthorizationService.requireCurrentPm(request.pmEmployeeNumber());
        if (request.plannedEndDate().isBefore(request.plannedStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }

        User pm = projectPmResolver.resolve(request.pmEmployeeNumber());

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description() == null ? null : request.description().trim());
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(request.plannedStartDate());
        project.setPlannedEndDate(request.plannedEndDate());

        Project savedProject = projectRepository.save(project);
        return new CreateProjectDraftResponse(
                savedProject.getId(),
                savedProject.getName(),
                savedProject.getPm().getEmployeeNumber(),
                savedProject.getStatus(),
                savedProject.getPlannedStartDate(),
                savedProject.getPlannedEndDate()
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> listProjects() {
        return projectRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(project -> new ProjectSummaryResponse(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        project.getPm().getEmployeeNumber(),
                        project.getStatus(),
                        project.getPlannedStartDate(),
                        project.getPlannedEndDate(),
                        project.getCreatedAt(),
                        project.getUpdatedAt()
                ))
                .toList();
    }

}
