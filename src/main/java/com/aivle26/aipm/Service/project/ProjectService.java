package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.ProjectSummaryResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectCostEstimateRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementChangeCandidateRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleScenarioRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.risk.RiskTeamMemberRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectDocumentAnalysisResultRepository projectDocumentAnalysisResultRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequirementChangeCandidateRepository projectRequirementChangeCandidateRepository;
    private final ProjectRequiredArtifactRepository projectRequiredArtifactRepository;
    private final ProjectKeyFeatureRepository projectKeyFeatureRepository;
    private final ProjectPlanningExtractionRepository projectPlanningExtractionRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectWbsResultRepository projectWbsResultRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final ProjectScheduleResultRepository projectScheduleResultRepository;
    private final ProjectScheduleScenarioRepository projectScheduleScenarioRepository;
    private final ProjectCostEstimateRepository projectCostEstimateRepository;
    private final RiskTeamMemberRepository riskTeamMemberRepository;

    // 현재 PM 소유 또는 STAFF 참여 범위의 프로젝트 요약만 반환한다.
    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> listProjects(AuthenticatedUser user) {
        List<Project> projects;
        if ("PM".equals(user.role())) {
            projects = projectRepository.findAllByPm_EmployeeNumberOrderByCreatedAtDesc(
                    user.employeeNumber()
            );
        } else if ("STAFF".equals(user.role())) {
            List<Long> participatingProjectIds =
                    riskTeamMemberRepository.findParticipatingProjectIds(user.employeeNumber());
            projects = participatingProjectIds.isEmpty()
                    ? List.of()
                    : projectRepository.findAllByIdInOrderByCreatedAtDesc(participatingProjectIds);
        } else {
            projects = List.of();
        }

        return projects.stream()
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

    // PM 소유권을 검증한 뒤 저장 파일, 문서 데이터, 프로젝트 순서로 삭제한다.
    @Transactional
    public void deleteProject(Long projectId, String pmEmployeeNumber) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."));

        if (!project.getPm().getEmployeeNumber().equals(pmEmployeeNumber)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_DELETE_FORBIDDEN", "해당 프로젝트를 삭제할 권한이 없습니다.");
        }

        projectDocumentService.deleteProjectDocumentFiles(projectId);

        projectScheduleRepository.deletePredecessorLinksByProjectId(projectId);
        projectScheduleScenarioRepository.deleteAllByProjectSchedule_Project_Id(projectId);
        projectScheduleRepository.deleteAllByProjectId(projectId);
        projectScheduleResultRepository.deleteAllByProjectId(projectId);
        projectCostEstimateRepository.deleteAllByProjectId(projectId);

        projectWbsTaskRepository.deleteRequirementLinksByProjectId(projectId);
        projectWbsTaskRepository.deleteSkillLinksByProjectId(projectId);
        projectWbsTaskRepository.clearParentTasksByProjectId(projectId);
        projectWbsTaskRepository.deleteAllByProjectId(projectId);
        projectWbsResultRepository.deleteAllByProjectId(projectId);

        projectPlanningExtractionRepository.deleteAllByProjectId(projectId);
        projectKeyFeatureRepository.deleteAllByProjectId(projectId);
        projectRequiredArtifactRepository.deleteAllByProjectId(projectId);
        projectRequirementChangeCandidateRepository.deleteAllByProjectId(projectId);
        projectRequirementRepository.deleteAllByProjectId(projectId);
        projectDocumentAnalysisResultRepository.deleteAllByProjectId(projectId);
        projectDocumentService.deleteProjectDocumentRecords(projectId);
        projectRepository.delete(project);
    }
}
