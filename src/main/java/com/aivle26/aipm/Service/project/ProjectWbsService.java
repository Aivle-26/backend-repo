package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationResponse;
import com.aivle26.aipm.Dto.project.ProjectWbsResponse;
import com.aivle26.aipm.Dto.project.SaveFinalWbsRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultResponse;
import com.aivle26.aipm.Dto.project.WbsTaskResultRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectKeyFeature;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsResult;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.WbsDifficulty;
import com.aivle26.aipm.Entity.project.WbsPhase;
import com.aivle26.aipm.Entity.project.WbsSkill;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.PlanningWbsClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectWbsService {
    private static final List<String> DEFAULT_METHODOLOGY =
            List.of("요구사항 분석", "설계", "개발", "테스트", "검수");
    private static final String NATIVE_WBS_AGENT_VERSION = "planning-wbs-native-v1";

    private final ProjectRepository projectRepository;
    private final ProjectKeyFeatureRepository projectKeyFeatureRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectRequiredArtifactRepository projectRequiredArtifactRepository;
    private final ProjectWbsResultRepository projectWbsResultRepository;
    private final ProjectWbsTaskRepository projectWbsTaskRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final PlanningWbsClient planningWbsClient;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ObjectMapper objectMapper;

    // 확정 요구사항으로 WBS를 생성한다. 재생성 시 AI 제안만 교체하고 사용자의 최종 WBS는 유지한다.
    @Transactional
    public ProjectWbsResponse generateWbs(Long projectId) {
        Project project = requireAuthorizedDraftProject(projectId);
        List<ProjectRequirement> requirements = getConfirmedRequirements(projectId);
        Map<Long, ProjectRequirement> requirementById = toRequirementMap(requirements);
        PlanningWbsGenerationResponse nativeResult =
                planningWbsClient.generateWbs(toGenerationRequest(project, requirements));
        SaveWbsResultRequest aiResult = toSaveWbsResult(nativeResult, requirementById);
        ProjectWbsResult savedResult = saveWbsSuggestion(project, aiResult, requirementById, nativeResult);
        return toResponse(savedResult);
    }

    // AI Server가 전달한 WBS만 저장한다. 기존 결과가 있으면 AI 제안만 교체하고 최종 WBS는 보존한다.
    @Transactional
    public SaveWbsResultResponse saveWbsResult(Long projectId, SaveWbsResultRequest request) {
        Project project = requireAuthorizedDraftProject(projectId);

        ProjectWbsResult savedResult = saveWbsSuggestion(
                project,
                request,
                toRequirementMap(getConfirmedRequirements(projectId)),
                null
        );
        return new SaveWbsResultResponse(
                savedResult.getId(),
                projectId,
                savedResult.getAgentExecutionId(),
                request.tasks().size()
        );
    }

    // 프로젝트별 AI 최초안과 현재 최종안을 한 번에 조회해 좌우 편집 화면을 복원한다.
    @Transactional(readOnly = true)
    public ProjectWbsResponse getWbs(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        getProject(projectId);
        ProjectWbsResult result = projectWbsResultRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wbs not found"));
        return toResponse(result);
    }

    // 사용자가 편집한 전체 목록을 원자적으로 교체 저장해 추가·수정·삭제·순서 변경을 반영한다.
    @Transactional
    public ProjectWbsResponse saveFinalWbs(Long projectId, SaveFinalWbsRequest request) {
        projectAuthorizationService.requireProjectPm(projectId);
        Project project = getDraftProject(projectId);
        ProjectWbsResult result = projectWbsResultRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wbs not found"));

        if (projectScheduleRepository.existsByProjectId(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "schedule already exists");
        }

        Map<Long, ProjectRequirement> requirementById = toRequirementMap(getConfirmedRequirements(projectId));
        List<WbsTaskResultRequest> tasksWithMetadata = mergeMissingTaskMetadata(
                request.tasks(),
                readInitialTasks(result.getInitialTasksJson(), List.of())
        );
        List<PreparedWbsTask> preparedTasks = prepareTasks(tasksWithMetadata, requirementById);
        deleteFinalTasks(projectId);
        saveTasks(project, result, preparedTasks, true);
        projectWbsTaskRepository.flush();
        return toResponse(result);
    }

    private ProjectWbsResult saveWbsSuggestion(
            Project project,
            SaveWbsResultRequest request,
            Map<Long, ProjectRequirement> requirementById,
            PlanningWbsGenerationResponse nativeResult
    ) {
        String agentExecutionId = requireText(request == null ? null : request.agentExecutionId(), "agentExecutionId");
        String agentVersion = requireText(request.agentVersion(), "agentVersion");
        if (projectWbsResultRepository.existsByAgentExecutionId(agentExecutionId)) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate agent execution id");
        }

        List<PreparedWbsTask> preparedTasks = prepareTasks(request.tasks(), requirementById);

        ProjectWbsResult existingResult = projectWbsResultRepository.findByProjectId(project.getId())
                .orElse(null);
        if (existingResult != null) {
            // 재생성 결과는 왼쪽 AI 제안에만 반영한다. 오른쪽 최종 WBS 작업 행은 수정하지 않는다.
            existingResult.setAgentExecutionId(agentExecutionId);
            existingResult.setAgentVersion(agentVersion);
            existingResult.setInitialTasksJson(writeInitialTasks(request.tasks()));
            applyAiMetadata(existingResult, nativeResult);
            return projectWbsResultRepository.save(existingResult);
        }

        // 최초 생성에서만 AI 제안을 오른쪽 편집용 WBS의 초깃값으로 함께 저장한다.
        ProjectWbsResult result = new ProjectWbsResult();
        result.setProject(project);
        result.setAgentExecutionId(agentExecutionId);
        result.setAgentVersion(agentVersion);
        result.setInitialTasksJson(writeInitialTasks(request.tasks()));
        applyAiMetadata(result, nativeResult);
        ProjectWbsResult savedResult = projectWbsResultRepository.save(result);

        saveTasks(project, savedResult, preparedTasks, false);
        projectWbsTaskRepository.flush();
        return savedResult;
    }

    private void saveTasks(
            Project project,
            ProjectWbsResult result,
            List<PreparedWbsTask> preparedTasks,
            boolean confirmed
    ) {
        Map<String, ProjectWbsTask> taskByExternalId = new LinkedHashMap<>();
        for (PreparedWbsTask preparedTask : preparedTasks) {
            ProjectWbsTask task = new ProjectWbsTask();
            task.setProject(project);
            task.setWbsResult(result);
            task.setExternalTaskId(preparedTask.externalTaskId());
            task.setParentExternalTaskId(preparedTask.parentExternalTaskId());
            task.setTaskCode(preparedTask.taskCode());
            task.setTaskName(preparedTask.taskName());
            task.setDescription(preparedTask.description());
            task.setPhase(preparedTask.phase());
            task.setRequiredSkills(new LinkedHashSet<>(preparedTask.requiredSkills()));
            task.setDifficulty(preparedTask.difficulty());
            task.setEstimatedHours(preparedTask.estimatedHours());
            task.setOrderIndex(preparedTask.orderIndex());
            task.setConfirmed(confirmed);
            task.setRelatedArtifactsJson(writeJson(preparedTask.relatedArtifacts()));
            task.setCompletionCriteriaJson(writeJson(preparedTask.completionCriteria()));
            task.setRequirements(new LinkedHashSet<>(preparedTask.requirements()));
            taskByExternalId.put(task.getExternalTaskId(), task);
        }

        projectWbsTaskRepository.saveAll(taskByExternalId.values());
        projectWbsTaskRepository.flush();

        List<ProjectWbsTask> childTasks = new ArrayList<>();
        for (ProjectWbsTask task : taskByExternalId.values()) {
            if (task.getParentExternalTaskId() != null) {
                task.setParentTask(taskByExternalId.get(task.getParentExternalTaskId()));
                childTasks.add(task);
            }
        }
        if (!childTasks.isEmpty()) {
            projectWbsTaskRepository.saveAll(childTasks);
        }
    }

    private ProjectWbsResponse toResponse(ProjectWbsResult result) {
        List<ProjectWbsTask> finalTasks = projectWbsTaskRepository
                .findByProjectIdOrderByOrderIndexAscIdAsc(result.getProject().getId());
        List<WbsTaskResultRequest> initialTasks = readInitialTasks(result.getInitialTasksJson(), finalTasks);

        return new ProjectWbsResponse(
                result.getId(),
                result.getProject().getId(),
                result.getAgentExecutionId(),
                result.getAgentVersion(),
                new ProjectWbsResponse.AiStatus(
                        result.getLlmStatus(),
                        result.getGenerationStatus(),
                        readJsonList(result.getWarningsJson(), String.class)
                ),
                toCoverage(result),
                !finalTasks.isEmpty() && finalTasks.stream().allMatch(ProjectWbsTask::isConfirmed),
                result.getCreatedAt(),
                initialTasks.stream()
                        .sorted(Comparator.comparingInt(WbsTaskResultRequest::orderIndex))
                        .map(this::toInitialTaskDetail)
                        .toList(),
                finalTasks.stream().map(this::toFinalTaskDetail).toList()
        );
    }

    private ProjectWbsResponse.WbsTaskDetail toInitialTaskDetail(WbsTaskResultRequest task) {
        return new ProjectWbsResponse.WbsTaskDetail(
                null,
                task.externalTaskId().trim(),
                normalizeNullable(task.parentExternalTaskId()),
                task.taskCode().trim(),
                task.taskName().trim(),
                task.description().trim(),
                parseEnum(task.phase(), WbsPhase.class),
                parseSkills(task.requiredSkills()),
                parseEnum(task.difficulty(), WbsDifficulty.class),
                task.estimatedHours(),
                task.orderIndex(),
                List.copyOf(task.requirementIds()),
                safeList(task.relatedArtifacts()),
                safeList(task.completionCriteria()),
                false
        );
    }

    private ProjectWbsResponse.WbsTaskDetail toFinalTaskDetail(ProjectWbsTask task) {
        List<Long> requirementIds = task.getRequirements().stream()
                .map(ProjectRequirement::getId)
                .sorted()
                .toList();
        return new ProjectWbsResponse.WbsTaskDetail(
                task.getId(),
                task.getExternalTaskId(),
                task.getParentExternalTaskId(),
                task.getTaskCode(),
                task.getTaskName(),
                task.getDescription(),
                task.getPhase(),
                new LinkedHashSet<>(task.getRequiredSkills()),
                task.getDifficulty(),
                task.getEstimatedHours(),
                task.getOrderIndex(),
                requirementIds,
                readRelatedArtifacts(task.getRelatedArtifactsJson()),
                readJsonList(task.getCompletionCriteriaJson(), String.class),
                task.isConfirmed()
        );
    }

    private PlanningWbsGenerationRequest toGenerationRequest(
            Project project,
            List<ProjectRequirement> requirements
    ) {
        List<String> keyFeatures = projectKeyFeatureRepository
                .findByProjectIdOrderByIdAsc(project.getId())
                .stream()
                .map(ProjectKeyFeature::getFeatureName)
                .toList();
        List<PlanningWbsGenerationRequest.RequiredArtifact> requiredArtifacts =
                projectRequiredArtifactRepository
                        .findByProjectIdOrderByIdAsc(project.getId())
                        .stream()
                        .map(this::toRequiredArtifact)
                        .toList();

        PlanningWbsGenerationRequest.ProjectInfo projectInfo =
                new PlanningWbsGenerationRequest.ProjectInfo(
                        project.getName(),
                        project.getDescription(),
                        project.getClientOrganization(),
                        project.getPlannedStartDate(),
                        project.getPlannedEndDate(),
                        keyFeatures,
                        requiredArtifacts,
                        readStringList(project.getAcceptanceConditionsJson()),
                        readStringList(project.getBudgetContractConditionsJson()),
                        readStringList(project.getSecurityPrivacyConditionsJson())
                );

        List<PlanningWbsGenerationRequest.RequirementData> candidates = requirements.stream()
                .sorted(Comparator.comparing(ProjectRequirement::getId))
                .map(requirement -> new PlanningWbsGenerationRequest.RequirementData(
                        requirement.getId(),
                        requirement.getTitle(),
                        requirement.getDescription(),
                        requirement.getType().name(),
                        requirement.getPriority().name(),
                        requirement.getAcceptanceCriteria(),
                        requirement.getDueDate(),
                        requirement.getDeliverableName(),
                        requirement.getSecurityCondition(),
                        requirementSourceDocument(requirement),
                        requirement.getSourceExcerpt()
                ))
                .toList();
        return new PlanningWbsGenerationRequest(projectInfo, candidates, DEFAULT_METHODOLOGY);
    }

    private PlanningWbsGenerationRequest.RequiredArtifact toRequiredArtifact(
            ProjectRequiredArtifact artifact
    ) {
        return new PlanningWbsGenerationRequest.RequiredArtifact(
                artifact.getArtifactType().name(),
                artifact.getArtifactName(),
                artifact.getRequiredVersion()
        );
    }

    private String requirementSourceDocument(ProjectRequirement requirement) {
        if (requirement.getSourceDocumentName() != null
                && !requirement.getSourceDocumentName().isBlank()) {
            return requirement.getSourceDocumentName().trim();
        }
        if (requirement.getSourceDocument() != null
                && requirement.getSourceDocument().getOriginalFileName() != null
                && !requirement.getSourceDocument().getOriginalFileName().isBlank()) {
            return requirement.getSourceDocument().getOriginalFileName().trim();
        }
        return "backend";
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values =
                    objectMapper.readValue(json, new TypeReference<List<String>>() {
                    });
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROJECT_PLANNING_DATA_CORRUPTED",
                    "프로젝트 계획 데이터를 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private SaveWbsResultRequest toSaveWbsResult(
            PlanningWbsGenerationResponse response,
            Map<Long, ProjectRequirement> requirementById
    ) {
        if (response == null || response.wbsItems() == null || response.wbsItems().isEmpty()) {
            throw invalidNativeWbsResponse("wbs_items must contain at least one item");
        }

        Map<Long, PlanningWbsGenerationResponse.WbsItem> itemById = new LinkedHashMap<>();
        for (PlanningWbsGenerationResponse.WbsItem item : response.wbsItems()) {
            if (item == null || item.wbsId() == null || item.wbsId() <= 0) {
                throw invalidNativeWbsResponse("invalid wbs_id");
            }
            if (itemById.putIfAbsent(item.wbsId(), item) != null) {
                throw invalidNativeWbsResponse("duplicate wbs_id");
            }
        }

        Map<Long, List<Long>> childIdsByParent = validateNativeHierarchy(itemById);
        Map<Long, List<Long>> requirementIdsByItem = new LinkedHashMap<>();
        Map<Long, WbsDifficulty> difficultyByItem = new LinkedHashMap<>();
        for (PlanningWbsGenerationResponse.WbsItem item : response.wbsItems()) {
            List<Long> requirementIds =
                    normalizeRequirementIds(item.mappedRequirementIds(), requirementById);
            requirementIdsByItem.put(item.wbsId(), requirementIds);
            difficultyByItem.put(
                    item.wbsId(),
                    resolveDifficulty(requirementIds, requirementById)
            );
        }

        Map<Long, Integer> estimatedHoursByItem = new HashMap<>();
        List<WbsTaskResultRequest> tasks = new ArrayList<>();
        for (int index = 0; index < response.wbsItems().size(); index++) {
            PlanningWbsGenerationResponse.WbsItem item = response.wbsItems().get(index);
            WbsPhase phase = resolvePhase(item, itemById);
            WbsDifficulty difficulty = difficultyByItem.get(item.wbsId());
            int estimatedHours = estimateHours(
                    item.wbsId(),
                    childIdsByParent,
                    difficultyByItem,
                    estimatedHoursByItem,
                    new LinkedHashSet<>()
            );
            tasks.add(new WbsTaskResultRequest(
                    externalTaskId(item.wbsId()),
                    item.parentWbsId() == null ? null : externalTaskId(item.parentWbsId()),
                    requireNativeText(item.wbsCode(), "wbs_code", 50),
                    requireNativeText(item.wbsName(), "wbs_name", 200),
                    requireNativeText(item.description(), "description", 2000),
                    phase.name(),
                    requiredSkills(phase).stream().map(Enum::name).toList(),
                    difficulty.name(),
                    estimatedHours,
                    index,
                    requirementIdsByItem.get(item.wbsId()),
                    safeList(item.relatedArtifacts()).stream()
                            .map(artifact -> new WbsTaskResultRequest.RelatedArtifact(
                                    artifact.artifactType(),
                                    artifact.artifactName(),
                                    artifact.requiredVersion()
                            ))
                            .toList(),
                    safeList(item.completionCriteria())
            ));
        }

        return new SaveWbsResultRequest(
                "wbs-native-" + UUID.randomUUID(),
                NATIVE_WBS_AGENT_VERSION,
                tasks
        );
    }

    private Map<Long, List<Long>> validateNativeHierarchy(
            Map<Long, PlanningWbsGenerationResponse.WbsItem> itemById
    ) {
        Map<Long, List<Long>> childIdsByParent = new LinkedHashMap<>();
        boolean hasTask = false;
        for (PlanningWbsGenerationResponse.WbsItem item : itemById.values()) {
            String itemType = requireNativeText(item.itemType(), "item_type", 30)
                    .toUpperCase(Locale.ROOT);
            int expectedLevel = switch (itemType) {
                case "PHASE" -> 1;
                case "WORK_PACKAGE" -> 2;
                case "TASK" -> {
                    hasTask = true;
                    yield 3;
                }
                default -> throw invalidNativeWbsResponse("invalid item_type");
            };
            if (item.level() == null || item.level() != expectedLevel) {
                throw invalidNativeWbsResponse("item_type and level do not match");
            }
            if (expectedLevel == 1 && item.parentWbsId() != null) {
                throw invalidNativeWbsResponse("phase must not have a parent");
            }
            if (expectedLevel > 1) {
                PlanningWbsGenerationResponse.WbsItem parent =
                        itemById.get(item.parentWbsId());
                if (parent == null) {
                    throw invalidNativeWbsResponse("parent_wbs_id was not found");
                }
                int expectedParentLevel = expectedLevel - 1;
                if (parent.level() == null || parent.level() != expectedParentLevel) {
                    throw invalidNativeWbsResponse("invalid parent hierarchy");
                }
                childIdsByParent
                        .computeIfAbsent(item.parentWbsId(), ignored -> new ArrayList<>())
                        .add(item.wbsId());
            }
        }
        for (Long itemId : itemById.keySet()) {
            resolveRootItem(itemId, itemById);
        }
        if (!hasTask) {
            throw invalidNativeWbsResponse("wbs_items must contain at least one TASK");
        }
        return childIdsByParent;
    }

    private PlanningWbsGenerationResponse.WbsItem resolveRootItem(
            Long itemId,
            Map<Long, PlanningWbsGenerationResponse.WbsItem> itemById
    ) {
        Set<Long> visited = new LinkedHashSet<>();
        PlanningWbsGenerationResponse.WbsItem current = itemById.get(itemId);
        while (current != null && current.parentWbsId() != null) {
            if (!visited.add(current.wbsId())) {
                throw invalidNativeWbsResponse("cyclic wbs hierarchy");
            }
            current = itemById.get(current.parentWbsId());
        }
        if (current == null) {
            throw invalidNativeWbsResponse("parent_wbs_id was not found");
        }
        return current;
    }

    private List<Long> normalizeRequirementIds(
            List<Long> values,
            Map<Long, ProjectRequirement> requirementById
    ) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long requirementId : values) {
            if (requirementId == null || !requirementById.containsKey(requirementId)) {
                throw invalidNativeWbsResponse("mapped_requirement_ids contains an unknown id");
            }
            normalized.add(requirementId);
        }
        return List.copyOf(normalized);
    }

    private WbsDifficulty resolveDifficulty(
            List<Long> requirementIds,
            Map<Long, ProjectRequirement> requirementById
    ) {
        boolean hasMediumOrUnspecified = false;
        for (Long requirementId : requirementIds) {
            String priority = requirementById.get(requirementId).getPriority().name();
            if ("HIGH".equals(priority)) {
                return WbsDifficulty.HIGH;
            }
            if (!"LOW".equals(priority)) {
                hasMediumOrUnspecified = true;
            }
        }
        return requirementIds.isEmpty() || hasMediumOrUnspecified
                ? WbsDifficulty.MEDIUM
                : WbsDifficulty.LOW;
    }

    private int estimateHours(
            Long itemId,
            Map<Long, List<Long>> childIdsByParent,
            Map<Long, WbsDifficulty> difficultyByItem,
            Map<Long, Integer> cache,
            Set<Long> visiting
    ) {
        Integer cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(itemId)) {
            throw invalidNativeWbsResponse("cyclic wbs hierarchy");
        }
        List<Long> childIds = childIdsByParent.getOrDefault(itemId, List.of());
        long hours;
        if (childIds.isEmpty()) {
            hours = switch (difficultyByItem.get(itemId)) {
                case LOW -> 8;
                case MEDIUM -> 16;
                case HIGH -> 24;
            };
        } else {
            hours = 0;
            for (Long childId : childIds) {
                hours += estimateHours(
                        childId,
                        childIdsByParent,
                        difficultyByItem,
                        cache,
                        visiting
                );
            }
        }
        visiting.remove(itemId);
        if (hours <= 0 || hours > Integer.MAX_VALUE) {
            throw invalidNativeWbsResponse("invalid estimated hours");
        }
        int normalizedHours = (int) hours;
        cache.put(itemId, normalizedHours);
        return normalizedHours;
    }

    private WbsPhase resolvePhase(
            PlanningWbsGenerationResponse.WbsItem item,
            Map<Long, PlanningWbsGenerationResponse.WbsItem> itemById
    ) {
        PlanningWbsGenerationResponse.WbsItem root =
                resolveRootItem(item.wbsId(), itemById);
        String name = root.wbsName() == null
                ? ""
                : root.wbsName().trim().toLowerCase(Locale.ROOT);
        if (name.contains("요구") || name.contains("분석") || name.contains("analysis")) {
            return WbsPhase.ANALYSIS;
        }
        if (name.contains("설계") || name.contains("design")) {
            return WbsPhase.DESIGN;
        }
        if (name.contains("개발") || name.contains("구현") || name.contains("develop")) {
            return WbsPhase.DEVELOPMENT;
        }
        if (name.contains("테스트") || name.contains("검수") || name.contains("test")) {
            return WbsPhase.TEST;
        }
        if (name.contains("배포") || name.contains("deploy")) {
            return WbsPhase.DEPLOYMENT;
        }
        if (name.contains("운영") || name.contains("operation")) {
            return WbsPhase.OPERATION;
        }

        String code = requireNativeText(root.wbsCode(), "wbs_code", 50);
        try {
            int phaseIndex = Integer.parseInt(code.split("\\.", 2)[0]);
            return switch (phaseIndex) {
                case 1 -> WbsPhase.ANALYSIS;
                case 2 -> WbsPhase.DESIGN;
                case 3 -> WbsPhase.DEVELOPMENT;
                case 4 -> WbsPhase.TEST;
                case 5 -> WbsPhase.DEPLOYMENT;
                case 6 -> WbsPhase.OPERATION;
                default -> WbsPhase.DEVELOPMENT;
            };
        } catch (NumberFormatException exception) {
            return WbsPhase.DEVELOPMENT;
        }
    }

    private Set<WbsSkill> requiredSkills(WbsPhase phase) {
        return switch (phase) {
            case ANALYSIS -> Set.of(WbsSkill.REQUIREMENTS_ANALYSIS);
            case DESIGN -> Set.of(WbsSkill.ARCHITECTURE_DESIGN);
            case DEVELOPMENT -> Set.of(
                    WbsSkill.BACKEND_DEVELOPMENT,
                    WbsSkill.FRONTEND_DEVELOPMENT
            );
            case TEST -> Set.of(WbsSkill.TESTING);
            case DEPLOYMENT, OPERATION -> Set.of(WbsSkill.DEVOPS);
        };
    }

    private String externalTaskId(Long wbsId) {
        return "WBS-" + wbsId;
    }

    private String requireNativeText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalidNativeWbsResponse(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidNativeWbsResponse(fieldName + " exceeds max length");
        }
        return normalized;
    }

    private ApiException invalidNativeWbsResponse(String detail) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_WBS_RESPONSE",
                "AI Server의 WBS 결과 형식이 올바르지 않습니다: " + detail
        );
    }

    private void deleteFinalTasks(Long projectId) {
        projectWbsTaskRepository.clearParentTasksByProjectId(projectId);
        projectWbsTaskRepository.deleteRequirementLinksByProjectId(projectId);
        projectWbsTaskRepository.deleteSkillLinksByProjectId(projectId);
        projectWbsTaskRepository.deleteAllByProjectId(projectId);
        projectWbsTaskRepository.flush();
    }

    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "project not found"));
    }

    private Project getDraftProject(Long projectId) {
        Project project = getProject(projectId);
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid project status");
        }
        return project;
    }

    private Project requireAuthorizedDraftProject(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        return getDraftProject(projectId);
    }

    private List<ProjectRequirement> getConfirmedRequirements(Long projectId) {
        List<ProjectRequirement> requirements = projectRequirementRepository
                .findByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED);
        if (requirements.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "confirmed requirement not found");
        }
        return requirements;
    }

    private Map<Long, ProjectRequirement> toRequirementMap(List<ProjectRequirement> requirements) {
        Map<Long, ProjectRequirement> requirementById = new LinkedHashMap<>();
        for (ProjectRequirement requirement : requirements) {
            requirementById.put(requirement.getId(), requirement);
        }
        return requirementById;
    }

    private String writeInitialTasks(List<WbsTaskResultRequest> tasks) {
        try {
            return objectMapper.writeValueAsString(tasks);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store wbs", exception);
        }
    }

    private void applyAiMetadata(
            ProjectWbsResult result,
            PlanningWbsGenerationResponse response
    ) {
        if (response == null) {
            return;
        }
        result.setLlmStatus(normalizeNullable(response.llmStatus()));
        result.setGenerationStatus(normalizeNullable(response.generationStatus()));
        result.setWarningsJson(writeJson(safeList(response.warnings())));
        result.setRequirementCoverageJson(writeJson(response.requirementCoverage()));
        result.setArtifactCoverageJson(writeJson(response.artifactCoverage()));
    }

    private ProjectWbsResponse.Coverage toCoverage(ProjectWbsResult result) {
        PlanningWbsGenerationResponse.RequirementCoverage requirements = readJson(
                result.getRequirementCoverageJson(),
                PlanningWbsGenerationResponse.RequirementCoverage.class
        );
        PlanningWbsGenerationResponse.ArtifactCoverage artifacts = readJson(
                result.getArtifactCoverageJson(),
                PlanningWbsGenerationResponse.ArtifactCoverage.class
        );
        return new ProjectWbsResponse.Coverage(
                requirements == null ? null : new ProjectWbsResponse.RequirementCoverage(
                        requirements.totalRequirements(),
                        requirements.mappedRequirements(),
                        safeList(requirements.unmappedRequirementIds()),
                        requirements.coverageRate()
                ),
                artifacts == null ? null : new ProjectWbsResponse.ArtifactCoverage(
                        artifacts.totalRequiredArtifacts(),
                        artifacts.mappedArtifacts(),
                        safeList(artifacts.unmappedArtifactTypes()),
                        artifacts.coverageRate()
                )
        );
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store wbs metadata", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WBS_DATA_CORRUPTED", exception);
        }
    }

    private <T> List<T> readJsonList(String json, Class<T> itemType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, itemType)
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WBS_DATA_CORRUPTED", exception);
        }
    }

    private List<WbsTaskResultRequest.RelatedArtifact> readRelatedArtifacts(String json) {
        return readJsonList(json, WbsTaskResultRequest.RelatedArtifact.class);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null
                ? List.of()
                : values.stream().filter(value -> value != null).toList();
    }

    private List<WbsTaskResultRequest> readInitialTasks(
            String initialTasksJson,
            List<ProjectWbsTask> fallbackTasks
    ) {
        if (initialTasksJson == null || initialTasksJson.isBlank()) {
            return fallbackTasks.stream().map(this::toTaskRequest).toList();
        }
        try {
            return objectMapper.readValue(
                    initialTasksJson,
                    new TypeReference<List<WbsTaskResultRequest>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "WBS_DATA_CORRUPTED",
                    "저장된 AI WBS 원본을 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private List<WbsTaskResultRequest> mergeMissingTaskMetadata(
            List<WbsTaskResultRequest> requestedTasks,
            List<WbsTaskResultRequest> aiSuggestionTasks
    ) {
        Map<String, WbsTaskResultRequest> suggestionByExternalId = new HashMap<>();
        for (WbsTaskResultRequest suggestion : safeList(aiSuggestionTasks)) {
            if (suggestion != null && suggestion.externalTaskId() != null) {
                suggestionByExternalId.put(suggestion.externalTaskId().trim(), suggestion);
            }
        }
        return safeList(requestedTasks).stream()
                .map(task -> {
                    if (task == null || task.externalTaskId() == null) {
                        return task;
                    }
                    WbsTaskResultRequest suggestion = suggestionByExternalId.get(
                            task.externalTaskId().trim()
                    );
                    if (suggestion == null
                            || (task.relatedArtifacts() != null && task.completionCriteria() != null)) {
                        return task;
                    }
                    return new WbsTaskResultRequest(
                            task.externalTaskId(),
                            task.parentExternalTaskId(),
                            task.taskCode(),
                            task.taskName(),
                            task.description(),
                            task.phase(),
                            task.requiredSkills(),
                            task.difficulty(),
                            task.estimatedHours(),
                            task.orderIndex(),
                            task.requirementIds(),
                            task.relatedArtifacts() == null
                                    ? safeList(suggestion.relatedArtifacts())
                                    : task.relatedArtifacts(),
                            task.completionCriteria() == null
                                    ? safeList(suggestion.completionCriteria())
                                    : task.completionCriteria()
                    );
                })
                .toList();
    }

    private WbsTaskResultRequest toTaskRequest(ProjectWbsTask task) {
        return new WbsTaskResultRequest(
                task.getExternalTaskId(),
                task.getParentExternalTaskId(),
                task.getTaskCode(),
                task.getTaskName(),
                task.getDescription(),
                task.getPhase().name(),
                task.getRequiredSkills().stream().map(Enum::name).toList(),
                task.getDifficulty().name(),
                task.getEstimatedHours(),
                task.getOrderIndex(),
                task.getRequirements().stream().map(ProjectRequirement::getId).sorted().toList(),
                readRelatedArtifacts(task.getRelatedArtifactsJson()),
                readJsonList(task.getCompletionCriteriaJson(), String.class)
        );
    }

    // WBS 작업의 필수값·외부 ID·부모·순서·요구사항 참조가 유효한지 검증한다.
    private List<PreparedWbsTask> prepareTasks(List<WbsTaskResultRequest> tasks, Map<Long, ProjectRequirement> requirementById) {
        if (tasks == null || tasks.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "wbs task not found");
        }

        List<PreparedWbsTask> preparedTasks = new ArrayList<>();
        Map<String, String> parentByExternalId = new LinkedHashMap<>();
        Set<String> taskCodes = new LinkedHashSet<>();
        Set<Integer> orderIndexes = new LinkedHashSet<>();

        for (WbsTaskResultRequest task : tasks) {
            if (task == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid wbs task");
            }
            String externalTaskId = requireText(task.externalTaskId(), "externalTaskId");
            String parentExternalTaskId = normalizeNullable(task.parentExternalTaskId());
            String taskCode = requireText(task.taskCode(), "taskCode");
            String taskName = requireText(task.taskName(), "taskName");
            String description = requireText(task.description(), "description");

            if (parentByExternalId.putIfAbsent(externalTaskId, parentExternalTaskId) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task external id");
            }
            if (!taskCodes.add(taskCode)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task code");
            }
            if (!orderIndexes.add(task.orderIndex())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate task order");
            }
            if (task.estimatedHours() <= 0 || task.orderIndex() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid wbs task");
            }
            if (parentExternalTaskId != null && externalTaskId.equals(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid parent task");
            }

            preparedTasks.add(new PreparedWbsTask(
                    externalTaskId,
                    parentExternalTaskId,
                    taskCode,
                    taskName,
                    description,
                    parseEnum(task.phase(), WbsPhase.class),
                    parseSkills(task.requiredSkills()),
                    parseEnum(task.difficulty(), WbsDifficulty.class),
                    task.estimatedHours(),
                    task.orderIndex(),
                    resolveRequirements(task.requirementIds(), requirementById),
                    safeList(task.relatedArtifacts()),
                    safeList(task.completionCriteria())
            ));
        }

        for (String parentExternalTaskId : parentByExternalId.values()) {
            if (parentExternalTaskId != null && !parentByExternalId.containsKey(parentExternalTaskId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "parent task not found");
            }
        }
        validateNoCycle(parentByExternalId);
        return preparedTasks;
    }

    private void validateNoCycle(Map<String, String> parentByExternalId) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String externalTaskId : parentByExternalId.keySet()) {
            if (!visited.contains(externalTaskId)) {
                walkParent(externalTaskId, parentByExternalId, visited, visiting);
            }
        }
    }

    private void walkParent(
            String externalTaskId,
            Map<String, String> parentByExternalId,
            Set<String> visited,
            Set<String> visiting
    ) {
        Deque<String> stack = new ArrayDeque<>();
        String current = externalTaskId;
        while (current != null && !visited.contains(current)) {
            if (!visiting.add(current)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "wbs cycle reference");
            }
            stack.push(current);
            current = parentByExternalId.get(current);
        }
        while (!stack.isEmpty()) {
            String node = stack.pop();
            visiting.remove(node);
            visited.add(node);
        }
    }

    private Set<ProjectRequirement> resolveRequirements(
            List<Long> requirementIds,
            Map<Long, ProjectRequirement> requirementById
    ) {
        if (requirementIds == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid requirement id");
        }
        Set<ProjectRequirement> requirements = new LinkedHashSet<>();
        for (Long requirementId : requirementIds) {
            ProjectRequirement requirement = requirementById.get(requirementId);
            if (requirement == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid requirement id");
            }
            requirements.add(requirement);
        }
        return requirements;
    }

    private Set<WbsSkill> parseSkills(List<String> values) {
        if (values == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
        Set<WbsSkill> skills = new LinkedHashSet<>();
        for (String value : values) {
            skills.add(parseEnum(value, WbsSkill.class));
        }
        return skills;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid enum");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PreparedWbsTask(
            String externalTaskId,
            String parentExternalTaskId,
            String taskCode,
            String taskName,
            String description,
            WbsPhase phase,
            Set<WbsSkill> requiredSkills,
            WbsDifficulty difficulty,
            int estimatedHours,
            int orderIndex,
            Set<ProjectRequirement> requirements,
            List<WbsTaskResultRequest.RelatedArtifact> relatedArtifacts,
            List<String> completionCriteria
    ) {
    }
}
