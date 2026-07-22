package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.ProjectRequirement;
import com.aivle26.aipm.Entity.RequirementPriority;
import com.aivle26.aipm.Entity.RequirementStatus;
import com.aivle26.aipm.Entity.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectDocumentAnalysisService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentAnalysisResultRepository analysisResultRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ObjectMapper objectMapper;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    public SaveDocumentAnalysisResultResponse saveAnalysisResult(Long projectId, SaveDocumentAnalysisResultRequest request) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));

        List<ProjectDocument> projectDocuments = projectDocumentRepository.findByProjectId(projectId);
        if (projectDocuments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "document not found");
        }
        if (analysisResultRepository.existsByAgentExecutionId(request.agentExecutionId().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }

        Map<Long, ProjectDocument> documentById = new LinkedHashMap<>();
        for (ProjectDocument projectDocument : projectDocuments) {
            documentById.put(projectDocument.getId(), projectDocument);
        }

        ProjectDocumentAnalysisResult analysisResult = new ProjectDocumentAnalysisResult();
        analysisResult.setProject(project);
        analysisResult.setAgentExecutionId(request.agentExecutionId().trim());
        analysisResult.setAgentVersion(request.agentVersion().trim());
        analysisResult.setProjectGoal(request.projectGoal().trim());
        analysisResult.setScope(request.scope().trim());
        analysisResult.setDeliverablesJson(toArrayJson(request.deliverables()));
        analysisResult.setMilestonesJson(toArrayJson(request.milestones()));
        analysisResult.setTechnologyStacksJson(toArrayJson(request.technologyStacks()));
        analysisResult.setConstraintsJson(toArrayJson(request.constraints()));
        analysisResult.setRisksJson(toArrayJson(request.risks()));

        ProjectDocumentAnalysisResult savedAnalysisResult = analysisResultRepository.save(analysisResult);

        List<ProjectRequirement> requirements = new ArrayList<>();
        for (DocumentAnalysisRequirementRequest requirementRequest : request.requirements()) {
            ProjectDocument sourceDocument = documentById.get(requirementRequest.sourceDocumentId());
            if (sourceDocument == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "document not found");
            }

            ProjectRequirement requirement = new ProjectRequirement();
            requirement.setProject(project);
            requirement.setAnalysisResult(savedAnalysisResult);
            requirement.setSourceDocument(sourceDocument);
            requirement.setExternalReferenceId(requirementRequest.externalReferenceId().trim());
            requirement.setType(parseRequirementType(requirementRequest.type()));
            requirement.setTitle(requirementRequest.title().trim());
            requirement.setDescription(requirementRequest.description().trim());
            requirement.setPriority(parseRequirementPriority(requirementRequest.priority()));
            requirement.setStatus(RequirementStatus.UNCONFIRMED);
            requirements.add(requirement);
        }

        projectRequirementRepository.saveAll(requirements);
        for (ProjectDocument projectDocument : projectDocuments) {
            projectDocument.setStatus(ProjectDocumentStatus.ANALYZED);
        }

        return new SaveDocumentAnalysisResultResponse(
                savedAnalysisResult.getId(),
                project.getId(),
                savedAnalysisResult.getAgentExecutionId(),
                requirements.size()
        );
    }

    private RequirementType parseRequirementType(String value) {
        return parseEnum(value, RequirementType.class);
    }

    private RequirementPriority parseRequirementPriority(String value) {
        return parseEnum(value, RequirementPriority.class);
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
    }

    private String toArrayJson(JsonNode node) {
        JsonNode normalizedNode = node;
        if (normalizedNode == null || normalizedNode.isNull()) {
            normalizedNode = objectMapper.createArrayNode();
        }
        if (!normalizedNode.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid request");
        }

        try {
            return objectMapper.writeValueAsString(normalizedNode);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid request");
        }
    }
}
