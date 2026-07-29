package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Mapper.project.ProjectRequirementMapper;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectRequirementImportService {
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequirementMapper projectRequirementMapper;

    public List<ProjectRequirement> importRequirements(
            Project project,
            ProjectDocumentAnalysisResult analysisResult,
            Map<String, ProjectDocument> documentByName,
            PlanningDocumentExtractionValidator.ValidatedResult result
    ) {
        List<ProjectRequirement> requirements = new ArrayList<>();
        List<PlanningDocumentExtractResponse.RequirementCandidate> candidates =
                result.response().requirementCandidates();

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
            projectRequirementMapper.captureAiSuggestion(requirement);
            requirements.add(requirement);
        }

        return projectRequirementRepository.saveAll(requirements);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
