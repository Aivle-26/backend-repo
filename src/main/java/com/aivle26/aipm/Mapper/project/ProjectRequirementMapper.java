package com.aivle26.aipm.Mapper.project;

import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import org.springframework.stereotype.Component;

@Component
public class ProjectRequirementMapper {

    // 요구사항과 연결 분석·문서 ID를 공통 API 상세 응답으로 변환한다.
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail toDetail(ProjectRequirement requirement) {
        return new ProjectDocumentAnalysisResultsResponse.RequirementDetail(
                requirement.getId(),
                requirement.getAnalysisResult() == null ? null : requirement.getAnalysisResult().getId(),
                requirement.getSourceDocument() == null ? null : requirement.getSourceDocument().getId(),
                requirement.getExternalReferenceId(),
                requirement.getType(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getAcceptanceCriteria(),
                requirement.getDueDate(),
                requirement.getDeliverableName(),
                requirement.getSecurityCondition(),
                requirement.getSourceDocumentName(),
                requirement.getSourceExcerpt(),
                requirement.getPriority(),
                requirement.getStatus(),
                requirement.isConfirmed(),
                requirement.getCreatedAt(),
                requirement.getUpdatedAt()
        );
    }
}
