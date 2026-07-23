package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.ProjectSummaryResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectPmResolver projectPmResolver;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectDocumentAnalysisResultRepository projectDocumentAnalysisResultRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository projectRequiredArtifactRepository;
    private final ProjectKeyFeatureRepository projectKeyFeatureRepository;
    private final ProjectPlanningExtractionRepository projectPlanningExtractionRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectWbsResultRepository projectWbsResultRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final ProjectScheduleResultRepository projectScheduleResultRepository;

    @Transactional
    public CreateProjectDraftResponse createProjectDraft(CreateProjectDraftRequest request) {
        LocalDate plannedStartDate = request.plannedStartDate();
        LocalDate plannedEndDate = request.plannedEndDate();
        if (plannedEndDate.isBefore(plannedStartDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid schedule date");
        }

        User pm = projectPmResolver.resolve(request.pmEmployeeNumber());

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description() == null ? null : request.description().trim());
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(plannedStartDate);
        project.setPlannedEndDate(plannedEndDate);

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

    @Transactional
    public void deleteProject(Long projectId, String pmEmployeeNumber) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."));

        if (!project.getPm().getEmployeeNumber().equals(pmEmployeeNumber)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_DELETE_FORBIDDEN", "해당 프로젝트를 삭제할 권한이 없습니다.");
        }

        projectDocumentService.deleteProjectDocumentFiles(projectId);

        projectScheduleRepository.deletePredecessorLinksByProjectId(projectId);
        projectScheduleRepository.deleteAllByProjectId(projectId);
        projectScheduleResultRepository.deleteAllByProjectId(projectId);

        projectWbsTaskRepository.deleteRequirementLinksByProjectId(projectId);
        projectWbsTaskRepository.deleteSkillLinksByProjectId(projectId);
        projectWbsTaskRepository.clearParentTasksByProjectId(projectId);
        projectWbsTaskRepository.deleteAllByProjectId(projectId);
        projectWbsResultRepository.deleteAllByProjectId(projectId);

        projectPlanningExtractionRepository.deleteAllByProjectId(projectId);
        projectKeyFeatureRepository.deleteAllByProjectId(projectId);
        projectRequiredArtifactRepository.deleteAllByProjectId(projectId);
        projectRequirementRepository.deleteAllByProjectId(projectId);
        projectDocumentAnalysisResultRepository.deleteAllByProjectId(projectId);
        projectDocumentService.deleteProjectDocumentRecords(projectId);
        projectRepository.delete(project);
    }
}
