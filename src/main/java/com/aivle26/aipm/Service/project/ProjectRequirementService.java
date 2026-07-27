package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectRequirementRequest;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectRequirementService {
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectRequirementMapper projectRequirementMapper;

    // 프로젝트와 연관 문서를 검증해 기본 미확정 요구사항을 저장하고 상세 응답을 반환한다.
    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail create(
            Long projectId,
            CreateProjectRequirementRequest request
    ) {
        Project project = getProject(projectId);
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(project);
        requirement.setAnalysisResult(resolveAnalysisResult(projectId, request.analysisResultId()));
        requirement.setSourceDocument(resolveSourceDocument(projectId, request.sourceDocumentId()));
        requirement.setExternalReferenceId(requirePositiveId(request.externalReferenceId(), "externalReferenceId"));
        requirement.setType(request.type());
        requirement.setTitle(requireText(request.title(), "title"));
        requirement.setDescription(requireText(request.description(), "description"));
        requirement.setAcceptanceCriteria(trimToNull(request.acceptanceCriteria()));
        requirement.setDueDate(request.dueDate());
        requirement.setDeliverableName(trimToNull(request.deliverableName()));
        requirement.setSecurityCondition(trimToNull(request.securityCondition()));
        requirement.setSourceDocumentName(trimToNull(request.sourceDocumentName()));
        requirement.setSourceExcerpt(trimToNull(request.sourceExcerpt()));
        requirement.setPriority(request.priority());
        requirement.setStatus(RequirementStatus.UNCONFIRMED);
        requirement.setConfirmed(false);
        return projectRequirementMapper.toDetail(projectRequirementRepository.save(requirement));
    }

    // 프로젝트 존재를 확인하고 선택 조건에 맞는 요구사항 목록을 상세 응답으로 반환한다.
    @Transactional(readOnly = true)
    public List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> findAll(
            Long projectId,
            RequirementType type,
            RequirementPriority priority,
            RequirementStatus status,
            Boolean confirmed
    ) {
        getProject(projectId);
        return projectRequirementRepository.findAllByFilters(projectId, type, priority, status, confirmed).stream()
                .map(projectRequirementMapper::toDetail)
                .toList();
    }

    // 프로젝트 소속 관계를 검증해 요구사항 한 건의 상세 정보를 반환한다.
    @Transactional(readOnly = true)
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail findOne(Long projectId, Long requirementId) {
        getProject(projectId);
        return projectRequirementMapper.toDetail(getRequirement(projectId, requirementId));
    }

    // 프로젝트 요구사항의 요청된 필드와 연관 정보를 검증·갱신해 상세 응답을 반환한다.
    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail update(
            Long projectId,
            Long requirementId,
            UpdateProjectRequirementRequest request
    ) {
        getProject(projectId);
        ProjectRequirement requirement = getRequirement(projectId, requirementId);

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
        return projectRequirementMapper.toDetail(projectRequirementRepository.saveAndFlush(requirement));
    }

    // 확정 또는 WBS 연결 여부를 확인한 뒤 삭제 가능한 프로젝트 요구사항을 제거한다.
    @Transactional
    public void delete(Long projectId, Long requirementId) {
        getProject(projectId);
        ProjectRequirement requirement = getRequirement(projectId, requirementId);
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

    // 프로젝트 요구사항을 CONFIRMED 상태와 confirmed=true로 변경해 반환한다.
    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail confirm(Long projectId, Long requirementId) {
        return changeState(projectId, requirementId, RequirementStatus.CONFIRMED, true);
    }

    // 프로젝트 요구사항을 UNCONFIRMED 상태와 confirmed=false로 변경해 반환한다.
    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail unconfirm(Long projectId, Long requirementId) {
        return changeState(projectId, requirementId, RequirementStatus.UNCONFIRMED, false);
    }

    // 프로젝트 요구사항을 REJECTED 상태와 confirmed=false로 변경해 반환한다.
    @Transactional
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail reject(Long projectId, Long requirementId) {
        return changeState(projectId, requirementId, RequirementStatus.REJECTED, false);
    }

    // 프로젝트의 모든 요구사항을 확정 상태로 갱신하고 변경된 목록을 반환한다.
    @Transactional
    public List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> confirmAll(Long projectId) {
        getProject(projectId);
        List<ProjectRequirement> requirements = projectRequirementRepository.findByProjectIdOrderByIdAsc(projectId);
        requirements.forEach(requirement -> {
            requirement.setStatus(RequirementStatus.CONFIRMED);
            requirement.setConfirmed(true);
        });
        projectRequirementRepository.flush();
        return requirements.stream().map(projectRequirementMapper::toDetail).toList();
    }

    // 프로젝트 소속 요구사항의 상태와 확정 여부를 함께 변경해 상세 응답으로 반환한다.
    private ProjectDocumentAnalysisResultsResponse.RequirementDetail changeState(
            Long projectId,
            Long requirementId,
            RequirementStatus status,
            boolean confirmed
    ) {
        getProject(projectId);
        ProjectRequirement requirement = getRequirement(projectId, requirementId);
        requirement.setStatus(status);
        requirement.setConfirmed(confirmed);
        return projectRequirementMapper.toDetail(projectRequirementRepository.saveAndFlush(requirement));
    }

    // 프로젝트 ID로 프로젝트를 조회하고 없으면 기존 NOT_FOUND 예외를 발생시킨다.
    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다. projectId=" + projectId
                ));
    }

    // 프로젝트와 요구사항 ID를 함께 조회해 소속이 일치하는 요구사항을 반환한다.
    private ProjectRequirement getRequirement(Long projectId, Long requirementId) {
        return projectRequirementRepository.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_REQUIREMENT_NOT_FOUND",
                        "프로젝트에 속한 요구사항을 찾을 수 없습니다. requirementId=" + requirementId
                ));
    }

    // 프로젝트에 속한 원본 문서를 조회하고 소속 관계를 검증해 반환한다.
    private ProjectDocument resolveSourceDocument(Long projectId, Long documentId) {
        return projectDocumentRepository.findByIdAndProjectId(documentId, projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUIREMENT_SOURCE_DOCUMENT",
                        "프로젝트에 속한 출처 문서를 찾을 수 없습니다. documentId=" + documentId
                ));
    }

    // 선택 분석 결과 ID가 프로젝트에 속하는지 검증하며 미지정 시 null을 반환한다.
    private ProjectDocumentAnalysisResult resolveAnalysisResult(Long projectId, Long analysisResultId) {
        if (analysisResultId == null) {
            return null;
        }
        return analysisResultRepository.findByIdAndProjectId(analysisResultId, projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUIREMENT_ANALYSIS_RESULT",
                        "프로젝트에 속한 분석 결과를 찾을 수 없습니다. analysisResultId=" + analysisResultId
                ));
    }

    // 필수 문자열을 검증하고 앞뒤 공백이 제거된 값을 반환한다.
    private String requireText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENT", field + " 값이 필요합니다.");
        }
        return normalized;
    }

    // 필수 외부 식별자가 양수인지 검증하고 원본 값을 반환한다.
    private Long requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENT", field + " 값은 양의 정수여야 합니다.");
        }
        return value;
    }

    // 선택 문자열의 공백을 제거하고 빈 값은 null로 반환한다.
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
