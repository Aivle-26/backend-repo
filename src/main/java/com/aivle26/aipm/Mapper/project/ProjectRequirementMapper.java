package com.aivle26.aipm.Mapper.project;

import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ProjectRequirementMapper {
    private final ObjectMapper objectMapper;

    // 현재 사용자 편집본을 공통 요구사항 응답으로 변환한다.
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

    // AI가 최초 생성한 값을 JSON 스냅샷으로 고정해 이후 사용자 편집과 분리한다.
    public void captureAiSuggestion(ProjectRequirement requirement) {
        AiSuggestionSnapshot snapshot = new AiSuggestionSnapshot(
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
                requirement.isConfirmed()
        );
        try {
            requirement.setAiSuggestionJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw snapshotError(exception);
        }
    }

    // 저장된 AI 최초 제안 스냅샷을 왼쪽 화면용 응답으로 복원한다.
    public ProjectDocumentAnalysisResultsResponse.RequirementDetail toAiSuggestion(ProjectRequirement requirement) {
        if (requirement.getAiSuggestionJson() == null || requirement.getAiSuggestionJson().isBlank()) {
            return null;
        }
        try {
            AiSuggestionSnapshot snapshot = objectMapper.readValue(
                    requirement.getAiSuggestionJson(),
                    AiSuggestionSnapshot.class
            );
            return new ProjectDocumentAnalysisResultsResponse.RequirementDetail(
                    requirement.getId(),
                    snapshot.analysisResultId(),
                    snapshot.sourceDocumentId(),
                    snapshot.externalReferenceId(),
                    snapshot.type(),
                    snapshot.title(),
                    snapshot.description(),
                    snapshot.acceptanceCriteria(),
                    snapshot.dueDate(),
                    snapshot.deliverableName(),
                    snapshot.securityCondition(),
                    snapshot.sourceDocumentName(),
                    snapshot.sourceExcerpt(),
                    snapshot.priority(),
                    snapshot.status(),
                    snapshot.confirmed(),
                    requirement.getCreatedAt(),
                    requirement.getCreatedAt()
            );
        } catch (JsonProcessingException exception) {
            throw snapshotError(exception);
        }
    }

    private ApiException snapshotError(JsonProcessingException exception) {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "REQUIREMENT_SNAPSHOT_ERROR",
                "요구사항 AI 제안 데이터를 처리할 수 없습니다.",
                exception
        );
    }

    private record AiSuggestionSnapshot(
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
            RequirementPriority priority,
            RequirementStatus status,
            boolean confirmed
    ) {
    }
}
