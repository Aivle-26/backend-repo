package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Dto.project.PlanningRequirementReadjustResponse;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.RequirementChangeCandidateResponse;
import com.aivle26.aipm.Dto.project.RequirementChangeProposal;
import com.aivle26.aipm.Dto.project.RequirementReadjustmentResponse;
import com.aivle26.aipm.Dto.project.ReviewRequirementChangeRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectRequirementChangeCandidate;
import com.aivle26.aipm.Entity.project.ProjectRequirementEvidence;
import com.aivle26.aipm.Entity.project.RequirementChangeReviewStatus;
import com.aivle26.aipm.Entity.project.RequirementChangeType;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Mapper.project.ProjectRequirementMapper;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementChangeCandidateRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProjectRequirementReadjustmentService {
    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequirementChangeCandidateRepository candidateRepository;
    private final ProjectDocumentService projectDocumentService;
    private final PlanningAgentClient planningAgentClient;
    private final ProjectRequirementMapper requirementMapper;
    private final ProjectAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RequirementReadjustmentResponse createCandidates(
            Long projectId,
            List<Long> requestedDocumentIds
    ) {
        List<Long> documentIds = requestedDocumentIds.stream()
                .distinct()
                .sorted()
                .toList();
        ReadjustmentPreparation preparation = inTransaction(
                () -> prepareReadjustment(projectId, documentIds)
        );
        List<StoredDocumentFile> files =
                projectDocumentService.getStoredDocumentFilesFromSnapshots(
                        preparation.documents()
                );
        PlanningRequirementReadjustResponse response =
                planningAgentClient.readjustRequirements(
                        files,
                        preparation.agentExistingRequirements()
                );

        if (response == null || response.changeCandidates() == null) {
            throw invalidAgentResponse(
                    "change_candidates must be present as an array."
            );
        }
        return inTransaction(() -> persistCandidates(preparation, response));
    }

    private ReadjustmentPreparation prepareReadjustment(
            Long projectId,
            List<Long> documentIds
    ) {
        authorizationService.requireProjectPm(projectId);
        Project project = requireProject(projectId);
        List<ProjectDocument> selectedDocuments =
                projectDocumentService.getAnalyzableProjectDocuments(
                        projectId,
                        documentIds
                );
        List<ProjectRequirement> existingRequirements =
                projectRequirementRepository.findByProjectIdOrderByIdAsc(projectId)
                        .stream()
                        .filter(ProjectRequirement::isIncludedInFinal)
                        .toList();
        if (existingRequirements.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROJECT_REQUIREMENTS_REQUIRED",
                    "Initial requirements must be analyzed before readjustment."
            );
        }

        ReadjustmentPreparation preparation = new ReadjustmentPreparation(
                projectId,
                project.getUpdatedAt(),
                documentIds,
                selectedDocuments.stream()
                        .map(ProjectDocumentService.StoredDocumentSnapshot::from)
                        .toList(),
                existingRequirements.stream()
                        .map(RequirementSnapshot::from)
                        .toList(),
                existingRequirements.stream()
                        .map(this::toAgentExistingRequirement)
                        .toList()
        );
        return preparation;
    }

    private RequirementReadjustmentResponse persistCandidates(
            ReadjustmentPreparation preparation,
            PlanningRequirementReadjustResponse response
    ) {
        Long projectId = preparation.projectId();
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        authorizationService.requireProjectPm(project);
        if (!Objects.equals(project.getUpdatedAt(), preparation.projectUpdatedAt())) {
            throw readjustmentInputChanged();
        }
        List<ProjectDocument> allDocuments =
                projectDocumentRepository.findAllForUpdate(projectId);
        Map<Long, ProjectDocument> documentById = new LinkedHashMap<>();
        Map<String, ProjectDocument> documentByName = new LinkedHashMap<>();
        allDocuments.forEach(document -> {
            documentById.put(document.getId(), document);
            documentByName.put(document.getOriginalFileName(), document);
        });
        List<ProjectDocument> selectedDocuments = preparation.documentIds().stream()
                .map(documentById::get)
                .filter(Objects::nonNull)
                .toList();
        if (selectedDocuments.size() != preparation.documentIds().size()
                || !selectedDocuments.stream()
                .map(ProjectDocumentService.StoredDocumentSnapshot::from)
                .toList()
                .equals(preparation.documents())) {
            throw readjustmentInputChanged();
        }
        List<ProjectRequirement> existingRequirements =
                projectRequirementRepository.findFinalForUpdate(projectId);
        if (!existingRequirements.stream()
                .map(RequirementSnapshot::from)
                .toList()
                .equals(preparation.requirements())) {
            throw readjustmentInputChanged();
        }
        List<PlanningRequirementReadjustResponse.ExistingRequirement>
                currentAgentExistingRequirements = existingRequirements.stream()
                        .map(this::toAgentExistingRequirement)
                        .toList();
        if (!currentAgentExistingRequirements.equals(
                preparation.agentExistingRequirements()
        )) {
            throw readjustmentInputChanged();
        }

        Map<Long, ProjectRequirement> existingById = new LinkedHashMap<>();
        existingRequirements.forEach(requirement ->
                existingById.put(requirement.getId(), requirement)
        );
        Set<Long> selectedDocumentIds = new HashSet<>(preparation.documentIds());

        List<ProjectRequirementChangeCandidate> candidates = new ArrayList<>();
        for (PlanningRequirementReadjustResponse.ChangeCandidate change :
                response.changeCandidates()) {
            candidates.add(toCandidate(
                    project,
                    change,
                    existingById,
                    documentById,
                    documentByName,
                    selectedDocumentIds
            ));
        }
        List<ProjectRequirementChangeCandidate> saved =
                candidateRepository.saveAll(candidates);
        allDocuments.forEach(document -> document.setStatus(
                selectedDocumentIds.contains(document.getId())
                        ? ProjectDocumentStatus.ANALYZED
                        : ProjectDocumentStatus.UPLOADED
        ));
        return new RequirementReadjustmentResponse(
                projectId,
                saved.stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public RequirementReadjustmentResponse listCandidates(Long projectId) {
        authorizationService.requireProjectPm(projectId);
        requireProject(projectId);
        return new RequirementReadjustmentResponse(
                projectId,
                candidateRepository.findByProjectIdOrderByIdAsc(projectId)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @Transactional
    public RequirementChangeCandidateResponse review(
            Long projectId,
            Long candidateId,
            ReviewRequirementChangeRequest request
    ) {
        authorizationService.requireProjectPm(projectId);
        ProjectRequirementChangeCandidate candidate =
                candidateRepository.findByIdAndProjectId(candidateId, projectId)
                        .orElseThrow(() -> candidateNotFound(candidateId));
        if (candidate.getAppliedAt() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUIREMENT_CHANGE_ALREADY_APPLIED",
                    "The requirement change candidate has already been applied."
            );
        }

        if (request.proposedRequirement() != null) {
            if (candidate.getChangeType() != RequirementChangeType.ADDED
                    && candidate.getChangeType() != RequirementChangeType.MODIFIED) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "REQUIREMENT_CHANGE_NOT_EDITABLE",
                        "Only added or modified proposals can be edited."
                );
            }
            RequirementChangeProposal proposal =
                    validateProposal(projectId, request.proposedRequirement());
            candidate.setProposedRequirementJson(writeProposal(proposal));
        }
        if (request.reviewStatus() == RequirementChangeReviewStatus.APPROVED
                && candidate.getChangeType() != RequirementChangeType.REMOVED
                && candidate.getChangeType() != RequirementChangeType.UNCHANGED
                && candidate.getProposedRequirementJson() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REQUIREMENT_CHANGE_PROPOSAL_REQUIRED",
                    "An approved added or modified candidate requires a proposal."
            );
        }
        candidate.setReviewStatus(request.reviewStatus());
        candidate.setReviewedAt(
                request.reviewStatus() == RequirementChangeReviewStatus.PENDING_REVIEW
                        ? null
                        : LocalDateTime.now()
        );
        return toResponse(candidateRepository.saveAndFlush(candidate));
    }

    @Transactional
    public ProjectRequirementsResponse apply(
            Long projectId,
            List<Long> requestedCandidateIds
    ) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        authorizationService.requireProjectPm(project);
        List<Long> candidateIds = requestedCandidateIds.stream()
                .distinct()
                .sorted()
                .toList();
        if (candidateIds.size() != requestedCandidateIds.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_REQUIREMENT_CHANGE_CANDIDATE",
                    "Duplicate requirement change candidate IDs are not allowed."
            );
        }
        List<ProjectRequirementChangeCandidate> candidates =
                candidateRepository.findForApply(projectId, candidateIds);
        if (candidates.size() != candidateIds.size()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REQUIREMENT_CHANGE_CANDIDATE_NOT_FOUND",
                    "One or more requirement change candidates were not found."
            );
        }

        // A locking read is required here: under MySQL REPEATABLE_READ a normal
        // SELECT could keep the snapshot created by the earlier authorization query.
        long nextExternalReferenceId = projectRequirementRepository
                .findAllForUpdate(projectId)
                .stream()
                .map(ProjectRequirement::getExternalReferenceId)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1;

        for (ProjectRequirementChangeCandidate candidate : candidates) {
            if (candidate.getAppliedAt() != null) {
                continue;
            }
            if (candidate.getReviewStatus() != RequirementChangeReviewStatus.APPROVED) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "REQUIREMENT_CHANGE_NOT_APPROVED",
                        "Only approved requirement changes can be applied."
                );
            }
            ProjectRequirement lockedRequirement =
                    lockExistingRequirement(candidate);
            assertNotStale(candidate, lockedRequirement);
            switch (candidate.getChangeType()) {
                case ADDED -> {
                    RequirementChangeProposal proposal =
                            readProposal(candidate.getProposedRequirementJson());
                    ProjectRequirement requirement = new ProjectRequirement();
                    requirement.setProject(project);
                    requirement.setExternalReferenceId(nextExternalReferenceId++);
                    requirement.setStatus(RequirementStatus.UNCONFIRMED);
                    requirement.setConfirmed(false);
                    requirement.setIncludedInFinal(true);
                    applyProposal(projectId, requirement, proposal, true);
                    requirementMapper.captureAiSuggestion(requirement);
                    projectRequirementRepository.save(requirement);
                }
                case MODIFIED -> {
                    applyProposal(
                            projectId,
                            lockedRequirement,
                            readProposal(candidate.getProposedRequirementJson()),
                            true
                    );
                    projectRequirementRepository.save(lockedRequirement);
                }
                case REMOVED -> {
                    lockedRequirement.setIncludedInFinal(false);
                    lockedRequirement.setStatus(RequirementStatus.REJECTED);
                    lockedRequirement.setConfirmed(false);
                    projectRequirementRepository.save(lockedRequirement);
                }
                case UNCHANGED -> {
                    // Approval records the PM decision; no requirement mutation is needed.
                }
            }
            candidate.setAppliedAt(LocalDateTime.now());
        }
        try {
            projectRequirementRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUIREMENT_REFERENCE_CONFLICT",
                    "A project requirement already uses the generated external reference ID.",
                    exception
            );
        }
        candidateRepository.flush();
        return buildRequirementsResponse(projectId);
    }

    private ProjectRequirementChangeCandidate toCandidate(
            Project project,
            PlanningRequirementReadjustResponse.ChangeCandidate change,
            Map<Long, ProjectRequirement> existingById,
            Map<Long, ProjectDocument> documentById,
            Map<String, ProjectDocument> documentByName,
            Set<Long> selectedDocumentIds
    ) {
        if (change == null) {
            throw invalidAgentResponse("Requirement change candidate is missing.");
        }
        RequirementChangeType changeType = parseEnum(
                change.changeType(),
                RequirementChangeType.class,
                "change_type"
        );
        ProjectRequirement existing = change.existingRequirementId() == null
                ? null
                : existingById.get(change.existingRequirementId());
        if (change.existingRequirementId() != null && existing == null) {
            throw invalidAgentResponse(
                    "Requirement change references an unknown existing requirement."
            );
        }
        RequirementChangeProposal proposed = change.proposedRequirement() == null
                ? null
                : toProposal(
                        change.proposedRequirement(),
                        documentById,
                        documentByName,
                        selectedDocumentIds
                );
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail>
                candidateEvidences = toEvidenceDetails(
                        change.evidences(),
                        documentById,
                        documentByName,
                        selectedDocumentIds
                );
        validateChangeShape(changeType, existing, proposed);

        ProjectRequirementChangeCandidate candidate =
                new ProjectRequirementChangeCandidate();
        candidate.setProject(project);
        candidate.setExistingRequirement(existing);
        candidate.setChangeType(changeType);
        candidate.setReviewStatus(RequirementChangeReviewStatus.PENDING_REVIEW);
        candidate.setChangeReason(requireText(
                change.changeReason(),
                "change_reason",
                1000
        ));
        RequirementChangeProposal existingProposal =
                existing == null ? null : toProposal(existing);
        candidate.setExistingRequirementJson(writeProposal(existingProposal));
        candidate.setProposedRequirementJson(writeProposal(proposed));
        candidate.setEvidencesJson(writeEvidenceDetails(candidateEvidences));
        candidate.setBaseRequirementUpdatedAt(
                existing == null ? null : existing.getUpdatedAt()
        );
        return candidate;
    }

    private PlanningRequirementReadjustResponse.ExistingRequirement toAgentExistingRequirement(
            ProjectRequirement requirement
    ) {
        return new PlanningRequirementReadjustResponse.ExistingRequirement(
                requirement.getId(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getType().name(),
                requirement.getPriority().name(),
                requirement.getAcceptanceCriteria(),
                requirement.getDueDate(),
                requirement.getDeliverableName(),
                requirement.getSecurityCondition(),
                requirement.getSourceDocument().getOriginalFileName(),
                requirement.getSourceExcerpt(),
                requirementMapper.toEvidenceDetails(requirement).stream()
                        .map(this::toAgentEvidence)
                        .toList()
        );
    }

    private PlanningDocumentExtractResponse.RequirementEvidence toAgentEvidence(
            ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail evidence
    ) {
        return new PlanningDocumentExtractResponse.RequirementEvidence(
                evidence.documentId(),
                evidence.sourceDocument(),
                evidence.pageNumber(),
                evidence.chunkId(),
                evidence.quoteText(),
                evidence.startOffset(),
                evidence.endOffset(),
                evidence.boundingBoxes().stream()
                        .map(box -> new PlanningDocumentExtractResponse.NormalizedBoundingBox(
                                box.x(),
                                box.y(),
                                box.width(),
                                box.height()
                        ))
                        .toList()
        );
    }

    private RequirementChangeProposal toProposal(ProjectRequirement requirement) {
        return new RequirementChangeProposal(
                requirement.getId(),
                requirement.getSourceDocument().getId(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getType(),
                requirement.getPriority(),
                requirement.getAcceptanceCriteria(),
                requirement.getDueDate(),
                requirement.getDeliverableName(),
                requirement.getSecurityCondition(),
                requirement.getSourceDocument().getOriginalFileName(),
                requirement.getSourceExcerpt(),
                requirementMapper.toEvidenceDetails(requirement)
        );
    }

    private RequirementChangeProposal toProposal(
            PlanningDocumentExtractResponse.RequirementCandidate source,
            Map<Long, ProjectDocument> documentById,
            Map<String, ProjectDocument> documentByName,
            Set<Long> selectedDocumentIds
    ) {
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> evidences =
                new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementEvidence evidence :
                source.evidences() == null
                        ? List.<PlanningDocumentExtractResponse.RequirementEvidence>of()
                        : source.evidences()) {
            if (evidence == null) {
                throw invalidAgentResponse("Requirement evidence is invalid.");
            }
            ProjectDocument document = evidence.documentId() == null
                    ? documentByName.get(evidence.sourceDocument())
                    : documentById.get(evidence.documentId());
            if (document == null
                    || !selectedDocumentIds.contains(document.getId())
                    || evidence.sourceDocument() == null
                    || !document.getOriginalFileName().equals(evidence.sourceDocument())) {
                throw invalidAgentResponse(
                        "Requirement evidence is not part of the selected documents."
                );
            }
            validateEvidence(evidence);
            evidences.add(new ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail(
                    null,
                    document.getId(),
                    document.getOriginalFileName(),
                    evidence.pageNumber(),
                    evidence.chunkId().trim(),
                    evidence.quoteText().trim(),
                    evidence.startOffset(),
                    evidence.endOffset(),
                    (evidence.boundingBoxes() == null
                            ? List.<PlanningDocumentExtractResponse.NormalizedBoundingBox>of()
                            : evidence.boundingBoxes()).stream()
                            .map(box -> new ProjectDocumentAnalysisResultsResponse.NormalizedBoundingBox(
                                    box.x(),
                                    box.y(),
                                    box.width(),
                                    box.height()
                            ))
                            .toList()
            ));
        }
        ProjectDocument sourceDocument = !evidences.isEmpty()
                ? documentById.get(evidences.get(0).documentId())
                : documentByName.get(source.sourceDocument());
        if (sourceDocument == null
                || !selectedDocumentIds.contains(sourceDocument.getId())) {
            throw invalidAgentResponse(
                    "Requirement source document is not part of the selected documents."
            );
        }
        return new RequirementChangeProposal(
                source.requirementId(),
                sourceDocument.getId(),
                requireText(source.functionName(), "function_name", 200),
                requireText(source.requirementText(), "requirement_text", 2000),
                parseEnum(source.category(), RequirementType.class, "category"),
                parseEnum(source.priority(), RequirementPriority.class, "priority"),
                trimToNull(source.acceptanceCriteria()),
                source.dueDate(),
                trimToNull(source.deliverableName()),
                trimToNull(source.securityCondition()),
                sourceDocument.getOriginalFileName(),
                !evidences.isEmpty()
                        ? evidences.get(0).quoteText()
                        : trimToNull(source.sourceExcerpt()),
                evidences
        );
    }

    private List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail>
            toEvidenceDetails(
                    List<PlanningDocumentExtractResponse.RequirementEvidence> source,
                    Map<Long, ProjectDocument> documentById,
                    Map<String, ProjectDocument> documentByName,
                    Set<Long> selectedDocumentIds
            ) {
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> result =
                new ArrayList<>();
        for (PlanningDocumentExtractResponse.RequirementEvidence evidence :
                source == null
                        ? List.<PlanningDocumentExtractResponse.RequirementEvidence>of()
                        : source) {
            if (evidence == null) {
                throw invalidAgentResponse("Requirement evidence is invalid.");
            }
            ProjectDocument document = evidence.documentId() == null
                    ? documentByName.get(evidence.sourceDocument())
                    : documentById.get(evidence.documentId());
            if (document == null
                    || !selectedDocumentIds.contains(document.getId())
                    || evidence.sourceDocument() == null
                    || !document.getOriginalFileName().equals(evidence.sourceDocument())) {
                throw invalidAgentResponse(
                        "Requirement evidence is not part of the selected documents."
                );
            }
            validateEvidence(evidence);
            result.add(new ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail(
                    null,
                    document.getId(),
                    document.getOriginalFileName(),
                    evidence.pageNumber(),
                    evidence.chunkId().trim(),
                    evidence.quoteText().trim(),
                    evidence.startOffset(),
                    evidence.endOffset(),
                    (evidence.boundingBoxes() == null
                            ? List.<PlanningDocumentExtractResponse.NormalizedBoundingBox>of()
                            : evidence.boundingBoxes()).stream()
                            .map(box -> new ProjectDocumentAnalysisResultsResponse.NormalizedBoundingBox(
                                    box.x(),
                                    box.y(),
                                    box.width(),
                                    box.height()
                            ))
                            .toList()
            ));
        }
        return result;
    }

    private RequirementChangeProposal validateProposal(
            Long projectId,
            RequirementChangeProposal proposal
    ) {
        if (proposal == null || proposal.sourceDocumentId() == null) {
            throw invalidProposal("A proposed source document is required.");
        }
        ProjectDocument sourceDocument = projectDocumentRepository
                .findByIdAndProjectId(proposal.sourceDocumentId(), projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUIREMENT_SOURCE_DOCUMENT",
                        "The proposed source document does not belong to the project."
                ));
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> sourceEvidences =
                proposal.evidences() == null ? List.of() : proposal.evidences();
        List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> evidences =
                new ArrayList<>();
        for (ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail evidence :
                sourceEvidences) {
            evidences.add(normalizeEvidenceDetail(projectId, evidence));
        }
        if (proposal.category() == null || proposal.priority() == null) {
            throw invalidProposal("Category and priority are required.");
        }
        return new RequirementChangeProposal(
                proposal.requirementId(),
                sourceDocument.getId(),
                requireUserText(proposal.functionName(), "functionName", 200),
                requireUserText(proposal.requirementText(), "requirementText", 2000),
                proposal.category(),
                proposal.priority(),
                trimToNull(proposal.acceptanceCriteria()),
                proposal.dueDate(),
                trimToNull(proposal.deliverableName()),
                trimToNull(proposal.securityCondition()),
                sourceDocument.getOriginalFileName(),
                !evidences.isEmpty()
                        ? evidences.getFirst().quoteText()
                        : trimToNull(proposal.sourceExcerpt()),
                evidences
        );
    }

    private void applyProposal(
            Long projectId,
            ProjectRequirement requirement,
            RequirementChangeProposal proposal,
            boolean replaceEvidence
    ) {
        RequirementChangeProposal validated = validateProposal(projectId, proposal);
        ProjectDocument sourceDocument = projectDocumentRepository
                .findByIdAndProjectId(validated.sourceDocumentId(), projectId)
                .orElseThrow();
        requirement.setSourceDocument(sourceDocument);
        requirement.setTitle(validated.functionName());
        requirement.setDescription(validated.requirementText());
        requirement.setType(validated.category());
        requirement.setPriority(validated.priority());
        requirement.setAcceptanceCriteria(validated.acceptanceCriteria());
        requirement.setDueDate(validated.dueDate());
        requirement.setDeliverableName(validated.deliverableName());
        requirement.setSecurityCondition(validated.securityCondition());
        requirement.setSourceDocumentName(sourceDocument.getOriginalFileName());
        requirement.setSourceExcerpt(validated.sourceExcerpt());
        if (replaceEvidence) {
            requirement.replaceEvidences(
                    validated.evidences().stream()
                            .map(evidence -> toEvidence(projectId, evidence))
                            .toList()
            );
        }
    }

    private ProjectRequirementEvidence toEvidence(
            Long projectId,
            ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail source
    ) {
        ProjectDocument document = projectDocumentRepository
                .findByIdAndProjectId(source.documentId(), projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUIREMENT_EVIDENCE",
                        "The evidence document does not belong to the project."
                ));
        ProjectRequirementEvidence evidence = new ProjectRequirementEvidence();
        evidence.setDocument(document);
        evidence.setPageNumber(source.pageNumber());
        evidence.setChunkId(source.chunkId().trim());
        evidence.setQuoteText(source.quoteText().trim());
        evidence.setStartOffset(source.startOffset());
        evidence.setEndOffset(source.endOffset());
        try {
            evidence.setBoundingBoxesJson(
                    objectMapper.writeValueAsString(source.boundingBoxes())
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUIREMENT_EVIDENCE",
                    "The evidence bounding boxes are invalid.",
                    exception
            );
        }
        return evidence;
    }

    private RequirementChangeCandidateResponse toResponse(
            ProjectRequirementChangeCandidate candidate
    ) {
        return new RequirementChangeCandidateResponse(
                candidate.getId(),
                candidate.getExistingRequirement() == null
                        ? null
                        : candidate.getExistingRequirement().getId(),
                candidate.getChangeType(),
                candidate.getReviewStatus(),
                candidate.getChangeReason(),
                readProposal(candidate.getExistingRequirementJson()),
                readProposal(candidate.getProposedRequirementJson()),
                readEvidenceDetails(candidate.getEvidencesJson()),
                candidate.getAppliedAt() != null,
                candidate.getCreatedAt(),
                candidate.getReviewedAt()
        );
    }

    private ProjectRequirementsResponse buildRequirementsResponse(Long projectId) {
        List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> aiSuggestions =
                projectRequirementRepository
                        .findByProjectIdAndAiSuggestionJsonIsNotNullAndIncludedInFinalTrueOrderByIdAsc(projectId)
                        .stream()
                        .map(requirementMapper::toAiSuggestion)
                        .toList();
        List<ProjectDocumentAnalysisResultsResponse.RequirementDetail> finalRequirements =
                projectRequirementRepository.findAllByFilters(
                                projectId,
                                null,
                                null,
                                null,
                                null
                        )
                        .stream()
                        .map(requirementMapper::toDetail)
                        .toList();
        return new ProjectRequirementsResponse(
                projectId,
                aiSuggestions,
                finalRequirements
        );
    }

    private void assertNotStale(
            ProjectRequirementChangeCandidate candidate,
            ProjectRequirement existing
    ) {
        if (candidate.getExistingRequirement() == null) {
            return;
        }
        if (existing == null
                || candidate.getBaseRequirementUpdatedAt() == null
                || !candidate.getBaseRequirementUpdatedAt().equals(
                        existing.getUpdatedAt()
                )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUIREMENT_CHANGE_STALE",
                    "The requirement changed after this candidate was created."
            );
        }
    }

    private ProjectRequirement lockExistingRequirement(
            ProjectRequirementChangeCandidate candidate
    ) {
        if (candidate.getExistingRequirement() == null) {
            return null;
        }
        ProjectRequirement existing = projectRequirementRepository.findForUpdate(
                        candidate.getExistingRequirement().getId(),
                        candidate.getProject().getId()
                )
                .orElse(null);
        if (existing == null || !existing.isIncludedInFinal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUIREMENT_CHANGE_STALE",
                    "The existing requirement is no longer available."
            );
        }
        return existing;
    }

    private void validateChangeShape(
            RequirementChangeType changeType,
            ProjectRequirement existing,
            RequirementChangeProposal proposed
    ) {
        boolean valid = switch (changeType) {
            case ADDED -> existing == null && proposed != null;
            case MODIFIED -> existing != null && proposed != null;
            case REMOVED -> existing != null && proposed == null;
            case UNCHANGED -> existing != null;
        };
        if (!valid) {
            throw invalidAgentResponse(
                    "Requirement change candidate shape does not match its change type."
            );
        }
    }

    private void validateEvidence(
            PlanningDocumentExtractResponse.RequirementEvidence evidence
    ) {
        if (evidence.chunkId() == null || evidence.chunkId().isBlank()
                || evidence.quoteText() == null || evidence.quoteText().isBlank()
                || evidence.pageNumber() != null && evidence.pageNumber() <= 0
                || (evidence.startOffset() == null) != (evidence.endOffset() == null)
                || evidence.startOffset() != null
                && (evidence.startOffset() < 0
                || evidence.endOffset() <= evidence.startOffset())) {
            throw invalidAgentResponse("Requirement evidence is invalid.");
        }
        if (evidence.boundingBoxes() != null) {
            evidence.boundingBoxes().forEach(box -> {
                if (box == null
                        || box.x() < 0 || box.y() < 0
                        || box.width() <= 0 || box.height() <= 0
                        || box.x() + box.width() > 1
                        || box.y() + box.height() > 1) {
                    throw invalidAgentResponse(
                            "Requirement evidence bounding box is invalid."
                    );
                }
            });
        }
    }

    private ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail
            normalizeEvidenceDetail(
                    Long projectId,
                    ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail evidence
            ) {
        if (evidence == null || evidence.documentId() == null) {
            throw invalidEvidence();
        }
        ProjectDocument document = projectDocumentRepository
                .findByIdAndProjectId(evidence.documentId(), projectId)
                .orElseThrow(this::invalidEvidence);
        if (evidence.sourceDocument() == null
                || !document.getOriginalFileName().equals(evidence.sourceDocument())) {
            throw invalidEvidence();
        }
        validateEvidenceDetail(evidence);
        return new ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail(
                null,
                document.getId(),
                document.getOriginalFileName(),
                evidence.pageNumber(),
                evidence.chunkId().trim(),
                evidence.quoteText().trim(),
                evidence.startOffset(),
                evidence.endOffset(),
                evidence.boundingBoxes() == null ? List.of() : evidence.boundingBoxes()
        );
    }

    private void validateEvidenceDetail(
            ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail evidence
    ) {
        if (evidence.chunkId() == null || evidence.chunkId().isBlank()
                || evidence.quoteText() == null || evidence.quoteText().isBlank()
                || evidence.pageNumber() != null && evidence.pageNumber() <= 0
                || (evidence.startOffset() == null) != (evidence.endOffset() == null)
                || evidence.startOffset() != null
                && (evidence.startOffset() < 0
                || evidence.endOffset() <= evidence.startOffset())) {
            throw invalidEvidence();
        }
        if (evidence.boundingBoxes() != null) {
            evidence.boundingBoxes().forEach(box -> {
                if (box == null
                        || !Double.isFinite(box.x())
                        || !Double.isFinite(box.y())
                        || !Double.isFinite(box.width())
                        || !Double.isFinite(box.height())
                        || box.x() < 0 || box.y() < 0
                        || box.width() <= 0 || box.height() <= 0
                        || box.x() + box.width() > 1
                        || box.y() + box.height() > 1) {
                    throw invalidEvidence();
                }
            });
        }
    }

    private RequirementChangeProposal readProposal(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RequirementChangeProposal.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REQUIREMENT_CHANGE_SNAPSHOT_ERROR",
                    "The requirement change snapshot could not be read.",
                    exception
            );
        }
    }

    private List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail>
            readEvidenceDetails(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> result =
                    objectMapper.readValue(
                            json,
                            new TypeReference<>() {
                            }
                    );
            return result == null ? List.of() : result;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REQUIREMENT_CHANGE_SNAPSHOT_ERROR",
                    "The requirement change evidence snapshot could not be read.",
                    exception
            );
        }
    }

    private String writeProposal(RequirementChangeProposal proposal) {
        if (proposal == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(proposal);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REQUIREMENT_CHANGE_SNAPSHOT_ERROR",
                    "The requirement change snapshot could not be written.",
                    exception
            );
        }
    }

    private String writeEvidenceDetails(
            List<ProjectDocumentAnalysisResultsResponse.RequirementEvidenceDetail> evidences
    ) {
        try {
            return objectMapper.writeValueAsString(
                    evidences == null ? List.of() : evidences
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REQUIREMENT_CHANGE_SNAPSHOT_ERROR",
                    "The requirement change evidence snapshot could not be written.",
                    exception
            );
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private ApiException readjustmentInputChanged() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "PROJECT_READJUSTMENT_INPUT_CHANGED",
                "The project, selected documents, or requirements changed during readjustment."
        );
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
    }

    private <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> enumType,
            String fieldName
    ) {
        try {
            return Enum.valueOf(
                    enumType,
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw invalidAgentResponse(fieldName + " is invalid.");
        }
    }

    private String requireText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw invalidAgentResponse(fieldName + " is invalid.");
        }
        return normalized;
    }

    private String requireUserText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw invalidProposal(fieldName + " is invalid.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ApiException invalidAgentResponse(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_AGENT_RESPONSE",
                message
        );
    }

    private ApiException invalidProposal(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUIREMENT_CHANGE_PROPOSAL",
                message
        );
    }

    private ApiException invalidEvidence() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUIREMENT_EVIDENCE",
                "The proposed requirement evidence is invalid."
        );
    }

    private ApiException candidateNotFound(Long candidateId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "REQUIREMENT_CHANGE_CANDIDATE_NOT_FOUND",
                "Requirement change candidate was not found: " + candidateId
        );
    }

    private record ReadjustmentPreparation(
            Long projectId,
            LocalDateTime projectUpdatedAt,
            List<Long> documentIds,
            List<ProjectDocumentService.StoredDocumentSnapshot> documents,
            List<RequirementSnapshot> requirements,
            List<PlanningRequirementReadjustResponse.ExistingRequirement>
                    agentExistingRequirements
    ) {
    }

    private record RequirementSnapshot(
            Long id,
            LocalDateTime updatedAt,
            boolean includedInFinal
    ) {
        private static RequirementSnapshot from(ProjectRequirement requirement) {
            return new RequirementSnapshot(
                    requirement.getId(),
                    requirement.getUpdatedAt(),
                    requirement.isIncludedInFinal()
            );
        }
    }
}
