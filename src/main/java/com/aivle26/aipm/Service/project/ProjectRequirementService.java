package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectRequirementRequest;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.SaveFinalRequirementsRequest;
import com.aivle26.aipm.Dto.project.UpdateProjectRequirementRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Mapper.project.ProjectRequirementMapper;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectRequirementService {
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectRequirementMapper projectRequirementMapper;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail create(
            Long projectId,
            CreateProjectRequirementRequest request
    ) {
        Project project = requireAccessibleProject(projectId);
        ProjectRequirement requirement = createRequirement(project, toValues(request));
        return projectRequirementMapper.toDetail(projectRequirementRepository.save(requirement));
    }

    // 왼쪽 AI 최초 제안과 오른쪽 사용자 편집본을 한 번에 반환한다.
    @Transactional(readOnly = true)
    public ProjectRequirementsResponse findAll(
            Long projectId,
            RequirementType type,
            RequirementPriority priority,
            RequirementStatus status,
            Boolean confirmed
    ) {
        requireAccessibleProject(projectId);
        List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> aiSuggestions =
                projectRequirementRepository.findByProjectIdAndAiSuggestionJsonIsNotNullOrderByIdAsc(projectId).stream()
                        .map(projectRequirementMapper::toAiSuggestion)
                        .toList();
        List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> finalRequirements =
                projectRequirementRepository.findAllByFilters(projectId, type, priority, status, confirmed).stream()
                        .map(projectRequirementMapper::toDetail)
                        .toList();
        return new ProjectRequirementsResponse(projectId, aiSuggestions, finalRequirements);
    }

    @Transactional(readOnly = true)
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail findOne(Long projectId, Long requirementId) {
        requireAccessibleProject(projectId);
        return projectRequirementMapper.toDetail(getFinalRequirement(projectId, requirementId));
    }

    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail update(
            Long projectId,
            Long requirementId,
            UpdateProjectRequirementRequest request
    ) {
        requireAccessibleProject(projectId);
        ProjectRequirement requirement = getFinalRequirement(projectId, requirementId);
        applyPartialUpdate(projectId, requirement, request);
        return projectRequirementMapper.toDetail(projectRequirementRepository.saveAndFlush(requirement));
    }

    // AI 제안 요구사항은 왼쪽 원본 보존을 위해 편집본에서만 제외하고, 직접 추가한 항목은 실제 삭제한다.
    @Transactional
    public void delete(Long projectId, Long requirementId) {
        requireAccessibleProject(projectId);
        ProjectRequirement requirement = getFinalRequirement(projectId, requirementId);
        if (requirement.getAiSuggestionJson() != null) {
            excludeFromFinal(requirement);
            projectRequirementRepository.save(requirement);
            return;
        }
        if (requirement.isConfirmed() || requirement.getStatus() == RequirementStatus.CONFIRMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUIREMENT_DELETE_CONFIRMED",
                    "확정된 요구사항은 삭제할 수 없습니다."
            );
        }
        if (projectWbsTaskRepository.countByRequirementId(requirementId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUIREMENT_DELETE_WBS_LINKED",
                    "WBS에 연결된 요구사항은 삭제할 수 없습니다."
            );
        }
        projectRequirementRepository.delete(requirement);
    }

    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail confirm(Long projectId, Long requirementId) {
        return changeState(projectId, requirementId, RequirementStatus.CONFIRMED, true);
    }

    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail unconfirm(Long projectId, Long requirementId) {
        return changeState(projectId, requirementId, RequirementStatus.UNCONFIRMED, false);
    }

    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail reject(Long projectId, Long requirementId) {
        return changeState(projectId, requirementId, RequirementStatus.REJECTED, false);
    }

    @Transactional
    public List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> confirmAll(Long projectId) {
        requireAccessibleProject(projectId);
        List<ProjectRequirement> requirements = projectRequirementRepository.findByProjectIdOrderByIdAsc(projectId)
                .stream()
                .filter(ProjectRequirement::isIncludedInFinal)
                .toList();
        requirements.forEach(requirement -> {
            requirement.setStatus(RequirementStatus.CONFIRMED);
            requirement.setConfirmed(true);
        });
        projectRequirementRepository.flush();
        return requirements.stream().map(projectRequirementMapper::toDetail).toList();
    }

    // 저장 버튼에서 전달한 전체 편집본을 ID 기준으로 추가·수정·제외하고 한 트랜잭션으로 반영한다.
    @Transactional
    public ProjectRequirementsResponse saveFinal(Long projectId, SaveFinalRequirementsRequest request) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));
        projectAuthorizationService.requireProjectPm(project);
        List<ProjectRequirement> existingRequirements =
                projectRequirementRepository.findByProjectIdOrderByIdAsc(projectId);
        Map<Long, ProjectRequirement> existingById = new LinkedHashMap<>();
        existingRequirements.forEach(requirement -> existingById.put(requirement.getId(), requirement));

        Set<Long> originallyFinalRequirementIds = new HashSet<>();
        List<ProjectRequirement> stagedFinalRequirements = existingRequirements.stream()
                .filter(ProjectRequirement::isIncludedInFinal)
                .toList();
        stagedFinalRequirements.forEach(requirement -> {
            originallyFinalRequirementIds.add(requirement.getId());
            requirement.setIncludedInFinal(false);
        });
        if (!stagedFinalRequirements.isEmpty()) {
            projectRequirementRepository.saveAll(stagedFinalRequirements);
            projectRequirementRepository.flush();
        }

        Set<Long> requestedIds = new HashSet<>();
        Set<Long> externalReferenceIds = new HashSet<>();
        List<ProjectRequirement> requirementsToSave = new ArrayList<>();

        for (SaveFinalRequirementsRequest.RequirementItem item : request.requirements()) {
            if (!externalReferenceIds.add(item.externalReferenceId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_REQUIREMENT_REFERENCE",
                        "중복된 externalReferenceId가 있습니다."
                );
            }

            ProjectRequirement requirement;
            if (item.requirementId() == null) {
                requirement = createRequirement(project, toValues(item));
            } else {
                if (!requestedIds.add(item.requirementId())) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "DUPLICATE_REQUIREMENT_ID",
                            "중복된 requirementId가 있습니다."
                    );
                }
                requirement = existingById.get(item.requirementId());
                if (requirement == null
                        || !originallyFinalRequirementIds.contains(requirement.getId())) {
                    throw requirementNotFound(item.requirementId());
                }
                applyValues(requirement, projectId, toValues(item));
            }
            requirement.setIncludedInFinal(true);
            requirementsToSave.add(requirement);
        }

        for (ProjectRequirement existing : existingRequirements) {
            if (!originallyFinalRequirementIds.contains(existing.getId())
                    || requestedIds.contains(existing.getId())) {
                continue;
            }
            if (mustPreserveRow(existing)) {
                excludeFromFinal(existing);
                requirementsToSave.add(existing);
            } else {
                projectRequirementRepository.delete(existing);
            }
        }

        projectRequirementRepository.saveAll(requirementsToSave);
        projectRequirementRepository.flush();
        return findAll(projectId, null, null, null, null);
    }

    private ProjectDocumentAnalysisResultsResponse.RequirementDetail changeState(
            Long projectId,
            Long requirementId,
            RequirementStatus status,
            boolean confirmed
    ) {
        requireAccessibleProject(projectId);
        ProjectRequirement requirement = getFinalRequirement(projectId, requirementId);
        requirement.setStatus(status);
        requirement.setConfirmed(confirmed);
        return projectRequirementMapper.toDetail(projectRequirementRepository.saveAndFlush(requirement));
    }

    private ProjectRequirement createRequirement(Project project, RequirementValues values) {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(project);
        requirement.setStatus(RequirementStatus.UNCONFIRMED);
        requirement.setConfirmed(false);
        requirement.setIncludedInFinal(true);
        applyValues(requirement, project.getId(), values);
        return requirement;
    }

    private void applyValues(ProjectRequirement requirement, Long projectId, RequirementValues values) {
        requirement.setAnalysisResult(resolveAnalysisResult(projectId, values.analysisResultId()));
        requirement.setSourceDocument(resolveSourceDocument(projectId, values.sourceDocumentId()));
        requirement.setExternalReferenceId(requirePositiveId(values.externalReferenceId(), "externalReferenceId"));
        requirement.setType(values.type());
        requirement.setTitle(requireText(values.title(), "title"));
        requirement.setDescription(requireText(values.description(), "description"));
        requirement.setAcceptanceCriteria(trimToNull(values.acceptanceCriteria()));
        requirement.setDueDate(values.dueDate());
        requirement.setDeliverableName(trimToNull(values.deliverableName()));
        requirement.setSecurityCondition(trimToNull(values.securityCondition()));
        requirement.setSourceDocumentName(trimToNull(values.sourceDocumentName()));
        requirement.setSourceExcerpt(trimToNull(values.sourceExcerpt()));
        requirement.setPriority(values.priority());
    }

    private void applyPartialUpdate(
            Long projectId,
            ProjectRequirement requirement,
            UpdateProjectRequirementRequest request
    ) {
        if (request.analysisResultId() != null) {
            requirement.setAnalysisResult(resolveAnalysisResult(projectId, request.analysisResultId()));
        }
        if (request.sourceDocumentId() != null) {
            requirement.setSourceDocument(resolveSourceDocument(projectId, request.sourceDocumentId()));
        }
        if (request.externalReferenceId() != null) {
            requirement.setExternalReferenceId(requirePositiveId(request.externalReferenceId(), "externalReferenceId"));
        }
        if (request.type() != null) {
            requirement.setType(request.type());
        }
        if (request.title() != null) {
            requirement.setTitle(requireText(request.title(), "title"));
        }
        if (request.description() != null) {
            requirement.setDescription(requireText(request.description(), "description"));
        }
        if (request.acceptanceCriteria() != null) {
            requirement.setAcceptanceCriteria(trimToNull(request.acceptanceCriteria()));
        }
        if (request.dueDate() != null) {
            requirement.setDueDate(request.dueDate());
        }
        if (request.deliverableName() != null) {
            requirement.setDeliverableName(trimToNull(request.deliverableName()));
        }
        if (request.securityCondition() != null) {
            requirement.setSecurityCondition(trimToNull(request.securityCondition()));
        }
        if (request.sourceDocumentName() != null) {
            requirement.setSourceDocumentName(trimToNull(request.sourceDocumentName()));
        }
        if (request.sourceExcerpt() != null) {
            requirement.setSourceExcerpt(trimToNull(request.sourceExcerpt()));
        }
        if (request.priority() != null) {
            requirement.setPriority(request.priority());
        }
    }

    private boolean mustPreserveRow(ProjectRequirement requirement) {
        return requirement.getAiSuggestionJson() != null
                || requirement.isConfirmed()
                || projectWbsTaskRepository.countByRequirementId(requirement.getId()) > 0;
    }

    private void excludeFromFinal(ProjectRequirement requirement) {
        requirement.setIncludedInFinal(false);
        requirement.setStatus(RequirementStatus.REJECTED);
        requirement.setConfirmed(false);
    }

    private Project requireAccessibleProject(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));
    }

    private ProjectRequirement getFinalRequirement(Long projectId, Long requirementId) {
        ProjectRequirement requirement = projectRequirementRepository.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(() -> requirementNotFound(requirementId));
        if (!requirement.isIncludedInFinal()) {
            throw requirementNotFound(requirementId);
        }
        return requirement;
    }

    private ApiException requirementNotFound(Long requirementId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "PROJECT_REQUIREMENT_NOT_FOUND",
                "프로젝트의 요구사항을 찾을 수 없습니다. requirementId=" + requirementId
        );
    }

    private ProjectDocument resolveSourceDocument(Long projectId, Long documentId) {
        return projectDocumentRepository.findByIdAndProjectId(documentId, projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUIREMENT_SOURCE_DOCUMENT",
                        "프로젝트의 출처 문서를 찾을 수 없습니다. documentId=" + documentId
                ));
    }

    private ProjectDocumentAnalysisResult resolveAnalysisResult(Long projectId, Long analysisResultId) {
        if (analysisResultId == null) {
            return null;
        }
        return analysisResultRepository.findByIdAndProjectId(analysisResultId, projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUIREMENT_ANALYSIS_RESULT",
                        "프로젝트의 분석 결과를 찾을 수 없습니다. analysisResultId=" + analysisResultId
                ));
    }

    private RequirementValues toValues(CreateProjectRequirementRequest request) {
        return new RequirementValues(
                request.analysisResultId(),
                request.sourceDocumentId(),
                request.externalReferenceId(),
                request.type(),
                request.title(),
                request.description(),
                request.acceptanceCriteria(),
                request.dueDate(),
                request.deliverableName(),
                request.securityCondition(),
                request.sourceDocumentName(),
                request.sourceExcerpt(),
                request.priority()
        );
    }

    private RequirementValues toValues(SaveFinalRequirementsRequest.RequirementItem item) {
        return new RequirementValues(
                item.analysisResultId(),
                item.sourceDocumentId(),
                item.externalReferenceId(),
                item.type(),
                item.title(),
                item.description(),
                item.acceptanceCriteria(),
                item.dueDate(),
                item.deliverableName(),
                item.securityCondition(),
                item.sourceDocumentName(),
                item.sourceExcerpt(),
                item.priority()
        );
    }

    private String requireText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUIREMENT",
                    field + " 값이 필요합니다."
            );
        }
        return normalized;
    }

    private Long requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUIREMENT",
                    field + " 값은 양의 정수여야 합니다."
            );
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record RequirementValues(
            Long analysisResultId,
            Long sourceDocumentId,
            Long externalReferenceId,
            RequirementType type,
            String title,
            String description,
            String acceptanceCriteria,
            LocalDate dueDate,
            String deliverableName,
            String securityCondition,
            String sourceDocumentName,
            String sourceExcerpt,
            RequirementPriority priority
    ) {
    }
}
