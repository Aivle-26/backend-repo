package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Config.storage.DocumentObjectStorage;
import com.aivle26.aipm.Dto.project.OrganizationChartArtifactResponse;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateRequest;
import com.aivle26.aipm.Dto.project.OrganizationChartGenerateResponse;
import com.aivle26.aipm.Dto.project.OrganizationChartHierarchyResponse;
import com.aivle26.aipm.Dto.project.OrganizationChartHierarchyUpdateRequest;
import com.aivle26.aipm.Dto.project.OrganizationChartRenderRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Service.ArtifactVersionComparator;
import com.aivle26.aipm.client.ai.GeneratedOrganizationChart;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationChartArtifactService {

    private static final String IMAGE_CONTENT_TYPE = "image/jpeg";
    private static final String STATE_CONTENT_TYPE = "application/json";
    private static final String IMAGE_EXTENSION = "jpg";
    private static final int STATE_SCHEMA_VERSION = 1;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_STATE_BYTES = 2 * 1024 * 1024;

    private final ProjectAuthorizationService projectAuthorizationService;
    private final PlanningResourceContextAssembler contextAssembler;
    private final PlanningResourceClient planningResourceClient;
    private final OrganizationChartArtifactPolicy artifactPolicy;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectDocumentRepository documentRepository;
    private final ProjectArtifactRepository artifactRepository;
    private final DocumentObjectStorage documentObjectStorage;
    private final ArtifactVersionComparator versionComparator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public OrganizationChartArtifactResponse generate(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        artifactPolicy.ensureForProject(projectId);

        PlanningResourceContextAssembler.PlanningResourceContext context =
                contextAssembler.assembleForOrganizationChart(projectId);
        PlanningResourceRecommendRequest planningRequest =
                withOrganizationMemberLabels(context);
        OrganizationChartGenerateRequest aiRequest = new OrganizationChartGenerateRequest(
                planningRequest,
                new OrganizationChartGenerateRequest.OrganizationMetadata(
                        context.projectManagerAiId(),
                        List.of()
                )
        );
        GeneratedOrganizationChart generated =
                planningResourceClient.generateOrganizationChart(aiRequest);
        validateImage(generated.image());

        StoredOrganizationChart state = buildStoredState(
                projectId,
                context,
                planningRequest,
                generated.response().organization()
        );
        return persistNewVersion(projectId, state, generated.image(), null);
    }

    public OrganizationChartArtifactResponse getLatest(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        artifactPolicy.ensureForProject(projectId);
        return toResponse(requireLatestArtifact(projectId));
    }

    public OrganizationChartHierarchyResponse getLatestStructure(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        artifactPolicy.ensureForProject(projectId);
        ProjectArtifact artifact = requireLatestArtifact(projectId);
        return toHierarchyResponse(artifact, loadState(projectId, artifact));
    }

    public OrganizationChartArtifactResponse updateHierarchy(
            Long projectId,
            OrganizationChartHierarchyUpdateRequest request
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        artifactPolicy.ensureForProject(projectId);

        ProjectArtifact baseArtifact = requireLatestArtifact(projectId);
        if (!baseArtifact.getVersion().equals(request.baseVersion())) {
            throw versionConflict();
        }
        StoredOrganizationChart current = loadState(projectId, baseArtifact);
        StoredOrganizationChart updated = applyHierarchy(projectId, current, request);
        GeneratedOrganizationChart rendered = planningResourceClient.renderOrganizationChart(
                new OrganizationChartRenderRequest(
                        updated.planningRequest(),
                        updated.organization()
                )
        );
        validateImage(rendered.image());
        StoredOrganizationChart renderedState = new StoredOrganizationChart(
                updated.schemaVersion(),
                updated.projectId(),
                updated.projectManagerMemberId(),
                updated.members(),
                updated.planningRequest(),
                rendered.response().organization()
        );
        return persistNewVersion(
                projectId,
                renderedState,
                rendered.image(),
                request.baseVersion()
        );
    }

    public OrganizationChartContent downloadLatest(Long projectId) {
        projectAuthorizationService.requireProjectAccess(projectId);
        ProjectArtifact artifact = requireLatestArtifact(projectId);
        ProjectDocument document = requireOwnedDocument(projectId, artifact);
        byte[] content;
        try {
            content = documentObjectStorage.get(document.getStoragePath());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_STORAGE_ERROR",
                    "The organization chart could not be downloaded.",
                    exception
            );
        }
        validateImage(content);
        return new OrganizationChartContent(
                "organization-chart-v" + artifact.getVersion() + ".jpg",
                IMAGE_CONTENT_TYPE,
                content
        );
    }

    private OrganizationChartArtifactResponse persistNewVersion(
            Long projectId,
            StoredOrganizationChart state,
            byte[] image,
            String expectedBaseVersion
    ) {
        byte[] stateBytes = serializeState(state);
        String objectBase = "projects/%d/artifacts/organization-chart/%s".formatted(
                projectId,
                UUID.randomUUID()
        );
        String stateKey = objectBase + ".json";
        String imageKey = objectBase + ".jpg";

        try {
            storeObject(stateKey, STATE_CONTENT_TYPE, stateBytes);
            storeObject(imageKey, IMAGE_CONTENT_TYPE, image);
        } catch (RuntimeException exception) {
            cleanupObjects(List.of(stateKey, imageKey));
            throw exception;
        }

        try {
            OrganizationChartArtifactResponse saved = transactionTemplate.execute(status ->
                    persistGeneratedArtifact(
                            projectId,
                            imageKey,
                            image.length,
                            expectedBaseVersion
                    )
            );
            if (saved == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ORGANIZATION_CHART_SAVE_FAILED",
                        "The organization chart could not be saved."
                );
            }
            return saved;
        } catch (RuntimeException exception) {
            cleanupObjects(List.of(stateKey, imageKey));
            throw exception;
        }
    }

    private OrganizationChartArtifactResponse persistGeneratedArtifact(
            Long projectId,
            String objectKey,
            long fileSize,
            String expectedBaseVersion
    ) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        artifactPolicy.ensure(project);
        List<ProjectArtifact> existing = artifactRepository
                .findByProjectIdAndArtifactType(
                        projectId,
                        OrganizationChartArtifactPolicy.TYPE
                );
        if (expectedBaseVersion != null) {
            String latest = existing.stream()
                    .map(ProjectArtifact::getVersion)
                    .max(versionComparator)
                    .orElse(null);
            if (!expectedBaseVersion.equals(latest)) {
                throw versionConflict();
            }
        }
        String version = nextVersion(existing);

        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.UPLOADED);
        document.setOriginalFileName("organization-chart.jpg");
        document.setStoredFileName(UUID.randomUUID() + ".jpg");
        document.setStoragePath(objectKey);
        document.setExtension(IMAGE_EXTENSION);
        document.setContentType(IMAGE_CONTENT_TYPE);
        document.setFileSize(fileSize);
        document.setFileType("ORGANIZATION_CHART");
        document.setProcessingMode(
                expectedBaseVersion == null
                        ? "AI_GENERATED"
                        : "MANUAL_HIERARCHY_EDIT"
        );
        ProjectDocument savedDocument = documentRepository.saveAndFlush(document);

        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setProject(project);
        artifact.setDocument(savedDocument);
        artifact.setArtifactType(OrganizationChartArtifactPolicy.TYPE);
        artifact.setArtifactName(OrganizationChartArtifactPolicy.NAME);
        artifact.setVersion(version);
        artifact.setApprovalStatus(ArtifactApprovalStatus.PENDING);
        ProjectArtifact savedArtifact = artifactRepository.saveAndFlush(artifact);
        return toResponse(savedArtifact);
    }

    private PlanningResourceRecommendRequest withOrganizationMemberLabels(
            PlanningResourceContextAssembler.PlanningResourceContext context
    ) {
        PlanningResourceRecommendRequest source = context.aiRequest();
        List<PlanningResourceRecommendRequest.ProjectMember> members = source.projectMembers()
                .stream()
                .map(member -> {
                    PlanningResourceContextAssembler.CandidateContext candidate =
                            context.candidatesByAiId().get(member.projectMemberId());
                    String label = candidate == null
                            ? member.memberName()
                            : candidate.user().getName() + " ("
                            + candidate.user().getEmployeeNumber() + ")";
                    return new PlanningResourceRecommendRequest.ProjectMember(
                            member.projectMemberId(),
                            label,
                            member.roles(),
                            member.skills(),
                            member.allocations()
                    );
                })
                .toList();
        return new PlanningResourceRecommendRequest(
                source.projectId(),
                source.projectName(),
                source.wbsTasks(),
                members
        );
    }

    private StoredOrganizationChart buildStoredState(
            Long projectId,
            PlanningResourceContextAssembler.PlanningResourceContext context,
            PlanningResourceRecommendRequest planningRequest,
            OrganizationChartGenerateResponse.OrganizationView organization
    ) {
        Map<Long, PlanningResourceContextAssembler.CandidateContext> candidates =
                context.candidatesByAiId();
        Map<String, OrganizationChartGenerateResponse.OrganizationTeam> teamsById =
                organization.teams().stream().collect(Collectors.toMap(
                        OrganizationChartGenerateResponse.OrganizationTeam::teamId,
                        Function.identity()
                ));
        Map<Long, OrganizationChartGenerateResponse.OrganizationTeam> teamsByAiMember =
                new LinkedHashMap<>();
        for (OrganizationChartGenerateResponse.OrganizationTeam team : organization.teams()) {
            if (team.memberIds() == null || team.memberIds().size() != 1) {
                throw invalidStructure("Each organization node must contain one member.");
            }
            Long aiMemberId = team.memberIds().get(0);
            if (!candidates.containsKey(aiMemberId)
                    || teamsByAiMember.put(aiMemberId, team) != null) {
                throw invalidStructure("Organization members must be unique request members.");
            }
        }
        if (!teamsByAiMember.keySet().equals(candidates.keySet())) {
            throw invalidStructure("Every request member must remain in the organization.");
        }

        PlanningResourceContextAssembler.CandidateContext projectManager =
                candidates.get(organization.projectManager());
        if (projectManager == null) {
            throw invalidStructure("The organization must have a real project manager.");
        }
        String projectManagerMemberId = projectManager.user().getEmployeeNumber();
        Map<String, Integer> nextOrderByParent = new HashMap<>();
        List<StoredMember> members = new ArrayList<>();
        for (OrganizationChartGenerateResponse.OrganizationTeam team : organization.teams()) {
            Long aiMemberId = team.memberIds().get(0);
            PlanningResourceContextAssembler.CandidateContext candidate = candidates.get(aiMemberId);
            String memberId = candidate.user().getEmployeeNumber();
            String parentMemberId = null;
            if (!aiMemberId.equals(organization.projectManager())) {
                if (team.reportsTo() == null) {
                    parentMemberId = projectManagerMemberId;
                } else {
                    OrganizationChartGenerateResponse.OrganizationTeam parent =
                            teamsById.get(team.reportsTo());
                    if (parent == null || parent.memberIds().size() != 1) {
                        throw invalidStructure("Organization parent must be a real member node.");
                    }
                    parentMemberId = candidates.get(parent.memberIds().get(0))
                            .user().getEmployeeNumber();
                }
            }
            int order = nextOrderByParent.merge(
                    parentMemberId == null ? "<root>" : parentMemberId,
                    1,
                    Integer::sum
            ) - 1;
            String jobFamily = team.primaryRoles().isEmpty()
                    ? null
                    : team.primaryRoles().get(0);
            members.add(new StoredMember(
                    memberId,
                    aiMemberId,
                    candidate.user().getName(),
                    jobFamily,
                    !team.primaryRoles().isEmpty(),
                    parentMemberId,
                    order
            ));
        }
        StoredOrganizationChart state = new StoredOrganizationChart(
                STATE_SCHEMA_VERSION,
                projectId,
                projectManagerMemberId,
                List.copyOf(members),
                planningRequest,
                organization
        );
        validateStoredTree(state);
        return state;
    }

    private StoredOrganizationChart applyHierarchy(
            Long projectId,
            StoredOrganizationChart state,
            OrganizationChartHierarchyUpdateRequest request
    ) {
        if (!projectId.equals(state.projectId())) {
            throw invalidHierarchy("Hierarchy project does not match the request project.");
        }
        Map<String, StoredMember> existingById = state.members().stream()
                .collect(Collectors.toMap(StoredMember::memberId, Function.identity()));
        Map<String, OrganizationChartHierarchyUpdateRequest.MemberHierarchy> updatesById =
                new LinkedHashMap<>();
        for (OrganizationChartHierarchyUpdateRequest.MemberHierarchy member : request.members()) {
            String memberId = member.memberId().trim();
            String parentMemberId = member.parentMemberId() == null
                    ? null
                    : member.parentMemberId().trim();
            if (parentMemberId != null && parentMemberId.isEmpty()) {
                parentMemberId = null;
            }
            OrganizationChartHierarchyUpdateRequest.MemberHierarchy normalized =
                    new OrganizationChartHierarchyUpdateRequest.MemberHierarchy(
                            memberId,
                            parentMemberId,
                            member.order()
                    );
            if (updatesById.put(memberId, normalized) != null) {
                throw invalidHierarchy("A member cannot appear more than once.");
            }
        }
        if (!updatesById.keySet().equals(existingById.keySet())) {
            throw invalidHierarchy("Hierarchy must preserve every organization member.");
        }
        if (!currentOrganizationMemberIds(projectId).equals(existingById.keySet())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ORGANIZATION_CHART_MEMBERS_CHANGED",
                    "Active project members changed; regenerate the organization chart."
            );
        }

        validateHierarchy(state.projectManagerMemberId(), updatesById);
        List<StoredMember> changed = updatesById.values().stream()
                .map(update -> {
                    StoredMember existing = existingById.get(update.memberId());
                    return new StoredMember(
                            existing.memberId(),
                            existing.aiMemberId(),
                            existing.memberName(),
                            existing.jobFamily(),
                            existing.capabilityRegistered(),
                            update.parentMemberId(),
                            update.order()
                    );
                })
                .toList();
        List<StoredMember> ordered = orderTree(state.projectManagerMemberId(), changed);
        OrganizationChartGenerateResponse.OrganizationView organization =
                applyMembersToOrganization(state.organization(), ordered);
        return new StoredOrganizationChart(
                state.schemaVersion(),
                state.projectId(),
                state.projectManagerMemberId(),
                ordered,
                state.planningRequest(),
                organization
        );
    }

    private Set<String> currentOrganizationMemberIds(Long projectId) {
        Project project = projectRepository.findWithPmById(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "Project was not found."
                ));
        Set<String> ids = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId)
                .stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ids.add(project.getPm().getEmployeeNumber());
        return ids;
    }

    private void validateHierarchy(
            String projectManagerMemberId,
            Map<String, OrganizationChartHierarchyUpdateRequest.MemberHierarchy> members
    ) {
        OrganizationChartHierarchyUpdateRequest.MemberHierarchy pm =
                members.get(projectManagerMemberId);
        if (pm == null || pm.parentMemberId() != null) {
            throw invalidHierarchy("The project manager must be the root member.");
        }
        long rootCount = members.values().stream()
                .filter(member -> member.parentMemberId() == null)
                .count();
        if (rootCount != 1) {
            throw invalidHierarchy("The project manager must be the only root member.");
        }

        Map<String, Set<Integer>> siblingOrders = new HashMap<>();
        for (OrganizationChartHierarchyUpdateRequest.MemberHierarchy member : members.values()) {
            String parent = member.parentMemberId();
            if (member.memberId().equals(parent)) {
                throw invalidHierarchy("A member cannot report to itself.");
            }
            if (parent != null && !members.containsKey(parent)) {
                throw invalidHierarchy("A parent must reference an organization member.");
            }
            String orderKey = parent == null ? "<root>" : parent;
            if (!siblingOrders.computeIfAbsent(orderKey, ignored -> new HashSet<>())
                    .add(member.order())) {
                throw invalidHierarchy("Sibling order values must be unique.");
            }
        }
        for (String memberId : members.keySet()) {
            Set<String> path = new HashSet<>();
            String current = memberId;
            while (current != null) {
                if (!path.add(current)) {
                    throw invalidHierarchy("Hierarchy relationships cannot form a cycle.");
                }
                current = members.get(current).parentMemberId();
            }
        }
    }

    private void validateStoredTree(StoredOrganizationChart state) {
        Map<String, OrganizationChartHierarchyUpdateRequest.MemberHierarchy> members =
                state.members().stream().collect(Collectors.toMap(
                        StoredMember::memberId,
                        member -> new OrganizationChartHierarchyUpdateRequest.MemberHierarchy(
                                member.memberId(),
                                member.parentMemberId(),
                                member.order()
                        ),
                        (left, right) -> {
                            throw invalidStructure("Organization members must be unique.");
                        },
                        LinkedHashMap::new
                ));
        try {
            validateHierarchy(state.projectManagerMemberId(), members);
        } catch (ApiException exception) {
            throw invalidStructure(exception.getMessage());
        }
    }

    private List<StoredMember> orderTree(
            String projectManagerMemberId,
            List<StoredMember> members
    ) {
        Map<String, List<StoredMember>> children = members.stream()
                .filter(member -> member.parentMemberId() != null)
                .collect(Collectors.groupingBy(StoredMember::parentMemberId));
        children.values().forEach(values -> values.sort(
                Comparator.comparingInt(StoredMember::order)
                        .thenComparing(StoredMember::memberId)
        ));
        Map<String, StoredMember> byId = members.stream()
                .collect(Collectors.toMap(StoredMember::memberId, Function.identity()));
        List<StoredMember> ordered = new ArrayList<>();
        appendTree(byId.get(projectManagerMemberId), children, ordered);
        return List.copyOf(ordered);
    }

    private void appendTree(
            StoredMember member,
            Map<String, List<StoredMember>> children,
            List<StoredMember> ordered
    ) {
        ordered.add(member);
        for (StoredMember child : children.getOrDefault(member.memberId(), List.of())) {
            appendTree(child, children, ordered);
        }
    }

    private OrganizationChartGenerateResponse.OrganizationView applyMembersToOrganization(
            OrganizationChartGenerateResponse.OrganizationView current,
            List<StoredMember> members
    ) {
        Map<Long, OrganizationChartGenerateResponse.OrganizationTeam> teamsByMember =
                current.teams().stream().collect(Collectors.toMap(
                        team -> team.memberIds().get(0),
                        Function.identity()
                ));
        Map<String, StoredMember> membersById = members.stream()
                .collect(Collectors.toMap(StoredMember::memberId, Function.identity()));
        List<OrganizationChartGenerateResponse.OrganizationTeam> teams = members.stream()
                .map(member -> {
                    OrganizationChartGenerateResponse.OrganizationTeam team =
                            teamsByMember.get(member.aiMemberId());
                    StoredMember parent = member.parentMemberId() == null
                            ? null
                            : membersById.get(member.parentMemberId());
                    String reportsTo = parent == null
                            ? null
                            : teamsByMember.get(parent.aiMemberId()).teamId();
                    return new OrganizationChartGenerateResponse.OrganizationTeam(
                            team.teamId(),
                            team.teamName(),
                            team.leaderMemberId(),
                            team.memberIds(),
                            team.primaryRoles(),
                            team.secondaryRoles(),
                            team.assignedWbsIds(),
                            reportsTo,
                            team.collaboratesWith(),
                            team.multiRoleMembers()
                    );
                })
                .toList();
        return new OrganizationChartGenerateResponse.OrganizationView(
                current.projectId(),
                current.projectManager(),
                teams,
                current.roleGaps(),
                current.unassignedWbsIds(),
                current.warnings(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private ProjectArtifact requireLatestArtifact(Long projectId) {
        return artifactRepository.findByProjectIdAndArtifactType(
                        projectId,
                        OrganizationChartArtifactPolicy.TYPE
                ).stream()
                .max(this::compareArtifacts)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ORGANIZATION_CHART_NOT_GENERATED",
                        "The organization chart has not been generated."
                ));
    }

    private int compareArtifacts(ProjectArtifact left, ProjectArtifact right) {
        int version = versionComparator.compare(left.getVersion(), right.getVersion());
        if (version != 0) {
            return version;
        }
        LocalDateTime leftTime = left.getUpdatedAt() == null
                ? LocalDateTime.MIN
                : left.getUpdatedAt();
        LocalDateTime rightTime = right.getUpdatedAt() == null
                ? LocalDateTime.MIN
                : right.getUpdatedAt();
        int updated = leftTime.compareTo(rightTime);
        if (updated != 0) {
            return updated;
        }
        return Comparator.nullsFirst(Long::compareTo).compare(left.getId(), right.getId());
    }

    private ProjectDocument requireOwnedDocument(Long projectId, ProjectArtifact artifact) {
        ProjectDocument document = artifact.getDocument();
        if (document == null
                || document.getProject() == null
                || !projectId.equals(document.getProject().getId())
                || document.getStoragePath() == null
                || document.getStoragePath().isBlank()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "ORGANIZATION_CHART_DOCUMENT_NOT_FOUND",
                    "The organization chart document was not found."
            );
        }
        return document;
    }

    private StoredOrganizationChart loadState(Long projectId, ProjectArtifact artifact) {
        ProjectDocument document = requireOwnedDocument(projectId, artifact);
        String stateKey = stateKey(document.getStoragePath());
        byte[] content;
        try {
            content = documentObjectStorage.get(stateKey);
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ORGANIZATION_CHART_STRUCTURE_NOT_FOUND",
                    "Regenerate the organization chart before editing its hierarchy.",
                    exception
            );
        }
        try {
            StoredOrganizationChart state = objectMapper.readValue(
                    content,
                    StoredOrganizationChart.class
            );
            if (state.schemaVersion() != STATE_SCHEMA_VERSION
                    || !projectId.equals(state.projectId())) {
                throw invalidStructure("Stored organization structure is incompatible.");
            }
            validateStoredTree(state);
            return state;
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_ORGANIZATION_CHART_STRUCTURE",
                    "The stored organization structure is invalid.",
                    exception
            );
        }
    }

    private byte[] serializeState(StoredOrganizationChart state) {
        try {
            byte[] content = objectMapper.writeValueAsBytes(state);
            if (content.length == 0 || content.length > MAX_STATE_BYTES) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ORGANIZATION_CHART_STRUCTURE_TOO_LARGE",
                        "The organization structure exceeds the size limit."
                );
            }
            return content;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ORGANIZATION_CHART_STRUCTURE_SERIALIZATION_FAILED",
                    "The organization structure could not be serialized.",
                    exception
            );
        }
    }

    private String nextVersion(List<ProjectArtifact> existing) {
        if (existing.isEmpty()) {
            return OrganizationChartArtifactPolicy.INITIAL_VERSION;
        }
        String latest = existing.stream()
                .map(ProjectArtifact::getVersion)
                .max(versionComparator)
                .orElse(OrganizationChartArtifactPolicy.INITIAL_VERSION);
        String[] segments = latest.split("\\.");
        segments[segments.length - 1] = new BigInteger(
                segments[segments.length - 1]
        ).add(BigInteger.ONE).toString();
        return String.join(".", segments);
    }

    private void storeObject(String objectKey, String contentType, byte[] content) {
        try {
            documentObjectStorage.put(
                    objectKey,
                    contentType,
                    content.length,
                    new ByteArrayInputStream(content)
            );
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_STORAGE_ERROR",
                    "The organization chart could not be uploaded.",
                    exception
            );
        }
    }

    private void validateImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "EMPTY_ORGANIZATION_CHART_IMAGE",
                    "The generated organization chart image is empty."
            );
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ORGANIZATION_CHART_IMAGE_TOO_LARGE",
                    "The generated organization chart exceeds the size limit."
            );
        }
        if (image.length < 3
                || (image[0] & 0xff) != 0xff
                || (image[1] & 0xff) != 0xd8
                || (image[2] & 0xff) != 0xff) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_ORGANIZATION_CHART_IMAGE",
                    "The generated organization chart is not a JPEG image."
            );
        }
    }

    private void cleanupObjects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                documentObjectStorage.delete(objectKey);
            } catch (RuntimeException cleanupFailure) {
                log.warn(
                        "Failed to remove organization chart object {} after failure",
                        objectKey,
                        cleanupFailure
                );
            }
        }
    }

    private String stateKey(String imageKey) {
        if (!imageKey.toLowerCase().endsWith(".jpg")) {
            throw invalidStructure("Organization chart image key is invalid.");
        }
        return imageKey.substring(0, imageKey.length() - 4) + ".json";
    }

    private OrganizationChartHierarchyResponse toHierarchyResponse(
            ProjectArtifact artifact,
            StoredOrganizationChart state
    ) {
        return new OrganizationChartHierarchyResponse(
                state.projectId(),
                artifact.getId(),
                artifact.getVersion(),
                state.projectManagerMemberId(),
                state.members().stream()
                        .map(member -> new OrganizationChartHierarchyResponse.MemberNode(
                                member.memberId(),
                                member.parentMemberId(),
                                member.memberName(),
                                member.jobFamily(),
                                member.order(),
                                member.capabilityRegistered()
                        ))
                        .toList()
        );
    }

    private OrganizationChartArtifactResponse toResponse(ProjectArtifact artifact) {
        ProjectDocument document = requireOwnedDocument(
                artifact.getProject().getId(),
                artifact
        );
        Long projectId = artifact.getProject().getId();
        String downloadPath = "/api/projects/%d/artifacts/organization-chart/latest/download"
                .formatted(projectId);
        return new OrganizationChartArtifactResponse(
                artifact.getId(),
                projectId,
                artifact.getArtifactType(),
                artifact.getArtifactName(),
                artifact.getVersion(),
                artifact.getApprovalStatus(),
                document.getContentType(),
                document.getFileSize(),
                artifact.getCreatedAt(),
                downloadPath,
                downloadPath
        );
    }

    private ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ORGANIZATION_CHART_VERSION_CONFLICT",
                "The organization chart changed; reload before saving."
        );
    }

    private ApiException invalidHierarchy(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ORGANIZATION_CHART_HIERARCHY",
                message
        );
    }

    private ApiException invalidStructure(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_ORGANIZATION_CHART_STRUCTURE",
                message
        );
    }

    private record StoredOrganizationChart(
            int schemaVersion,
            Long projectId,
            String projectManagerMemberId,
            List<StoredMember> members,
            PlanningResourceRecommendRequest planningRequest,
            OrganizationChartGenerateResponse.OrganizationView organization
    ) {
    }

    private record StoredMember(
            String memberId,
            Long aiMemberId,
            String memberName,
            String jobFamily,
            boolean capabilityRegistered,
            String parentMemberId,
            int order
    ) {
    }

    public record OrganizationChartContent(
            String fileName,
            String contentType,
            byte[] content
    ) {
        public OrganizationChartContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
