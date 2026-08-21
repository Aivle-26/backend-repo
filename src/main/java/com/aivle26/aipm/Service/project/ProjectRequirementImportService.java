package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectRequirementEvidence;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Mapper.project.ProjectRequirementMapper;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectRequirementImportService {
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequirementMapper projectRequirementMapper;
    private final ObjectMapper objectMapper;

    public List<ProjectRequirement> importRequirements(
            Project project,
            ProjectDocumentAnalysisResult analysisResult,
            Map<String, ProjectDocument> documentByName,
            PlanningDocumentExtractionValidator.ValidatedResult result
    ) {
        List<ProjectRequirement> requirements = new ArrayList<>();
        List<PlanningDocumentExtractResponse.RequirementCandidate> candidates =
                result.response().requirementCandidates();
        Map<Long, ProjectDocument> documentById = new LinkedHashMap<>();
        documentByName.values().forEach(document ->
                documentById.put(document.getId(), document)
        );

        for (int index = 0; index < candidates.size(); index++) {
            PlanningDocumentExtractResponse.RequirementCandidate candidate =
                    candidates.get(index);
            ProjectDocument sourceDocument = documentByName.get(candidate.sourceDocument());
            if (sourceDocument == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "INVALID_PLANNING_AGENT_RESPONSE",
                        "Requirement source document is not part of this analysis."
                );
            }

            ProjectRequirement requirement = new ProjectRequirement();
            requirement.setProject(project);
            requirement.setAnalysisResult(analysisResult);
            requirement.setSourceDocument(sourceDocument);
            requirement.setExternalReferenceId(candidate.requirementId());
            requirement.setType(result.requirementTypes().get(index));
            requirement.setTitle(candidate.functionName().trim());
            requirement.setDescription(candidate.requirementText().trim());
            requirement.setPriority(result.requirementPriorities().get(index));
            requirement.setStatus(RequirementStatus.UNCONFIRMED);
            requirement.setConfirmed(false);
            requirement.setAcceptanceCriteria(trimToNull(candidate.acceptanceCriteria()));
            requirement.setDueDate(candidate.dueDate());
            requirement.setDeliverableName(trimToNull(candidate.deliverableName()));
            requirement.setSecurityCondition(trimToNull(candidate.securityCondition()));
            requirement.setSourceDocumentName(candidate.sourceDocument().trim());
            requirement.setSourceExcerpt(trimToNull(candidate.sourceExcerpt()));
            requirement.setIncludedInFinal(true);
            for (PlanningDocumentExtractResponse.RequirementEvidence evidence :
                    candidate.evidences() == null ? List.<PlanningDocumentExtractResponse.RequirementEvidence>of() : candidate.evidences()) {
                requirement.addEvidence(toEvidence(
                        evidence,
                        documentByName,
                        documentById
                ));
            }
            projectRequirementMapper.captureAiSuggestion(requirement);
            requirements.add(requirement);
        }

        return projectRequirementRepository.saveAll(requirements);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProjectRequirementEvidence toEvidence(
            PlanningDocumentExtractResponse.RequirementEvidence source,
            Map<String, ProjectDocument> documentByName,
            Map<Long, ProjectDocument> documentById
    ) {
        if (source == null) {
            throw invalidEvidence("Requirement evidence is missing.");
        }
        ProjectDocument document = source.documentId() == null
                ? documentByName.get(source.sourceDocument())
                : documentById.get(source.documentId());
        if (document == null
                || source.sourceDocument() == null
                || !document.getOriginalFileName().equals(source.sourceDocument())) {
            throw invalidEvidence("Requirement evidence document is not part of this analysis.");
        }
        if (source.pageNumber() != null && source.pageNumber() <= 0) {
            throw invalidEvidence("Requirement evidence page_number is invalid.");
        }
        if (source.chunkId() == null || source.chunkId().isBlank()
                || source.quoteText() == null || source.quoteText().isBlank()) {
            throw invalidEvidence("Requirement evidence chunk or quote is missing.");
        }
        if ((source.startOffset() == null) != (source.endOffset() == null)
                || source.startOffset() != null
                && (source.startOffset() < 0 || source.endOffset() <= source.startOffset())) {
            throw invalidEvidence("Requirement evidence offsets are invalid.");
        }
        List<PlanningDocumentExtractResponse.NormalizedBoundingBox> boundingBoxes =
                source.boundingBoxes() == null ? List.of() : source.boundingBoxes();
        boundingBoxes.forEach(this::validateBoundingBox);

        ProjectRequirementEvidence evidence = new ProjectRequirementEvidence();
        evidence.setDocument(document);
        evidence.setPageNumber(source.pageNumber());
        evidence.setChunkId(source.chunkId().trim());
        evidence.setQuoteText(source.quoteText().trim());
        evidence.setStartOffset(source.startOffset());
        evidence.setEndOffset(source.endOffset());
        try {
            evidence.setBoundingBoxesJson(objectMapper.writeValueAsString(boundingBoxes));
        } catch (JsonProcessingException exception) {
            throw invalidEvidence("Requirement evidence bounding boxes are invalid.");
        }
        return evidence;
    }

    private void validateBoundingBox(
            PlanningDocumentExtractResponse.NormalizedBoundingBox box
    ) {
        if (box == null
                || box.x() < 0 || box.y() < 0
                || box.width() <= 0 || box.height() <= 0
                || box.x() + box.width() > 1
                || box.y() + box.height() > 1) {
            throw invalidEvidence("Requirement evidence bounding box is invalid.");
        }
    }

    private ApiException invalidEvidence(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_AGENT_RESPONSE",
                message
        );
    }
}
