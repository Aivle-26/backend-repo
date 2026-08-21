package com.aivle26.aipm.Service.project.weekly;

import com.aivle26.aipm.Dto.project.weekly.WeeklyScrumWorkflowModels;
import com.aivle26.aipm.Entity.project.WeeklyScrumReport;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WeeklyScrumReviewManager {
    private static final Set<String> REVIEW_STATUSES =
            Set.of("APPROVED", "MODIFIED", "REJECTED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");

    private final ProjectMemberRepository projectMemberRepository;
    private final ObjectMapper objectMapper;

    public ObjectNode apply(
            Long projectId,
            WeeklyScrumReport report,
            JsonNode reviewResponse,
            JsonNode recommendationResponse,
            WeeklyScrumWorkflowModels.SaveReviewRequest request
    ) {
        ArrayNode reviewedFindings = applyFindingDecisions(
                requireArray(reviewResponse, "review_findings"),
                request.findings()
        );
        ArrayNode reviewedActions = applyActionDecisions(
                projectId,
                report,
                reviewedFindings,
                requireArray(recommendationResponse, "recommended_next_actions"),
                request.actions()
        );
        ObjectNode result = objectMapper.createObjectNode();
        result.set("reviewed_findings", reviewedFindings);
        result.set("recommended_next_actions", reviewedActions);
        result.put("source_finding_count", reviewedFindings.size());
        result.put("source_action_count", reviewedActions.size());
        return result;
    }

    private ArrayNode applyFindingDecisions(
            ArrayNode originals,
            List<WeeklyScrumWorkflowModels.FindingDecision> decisions
    ) {
        Map<String, WeeklyScrumWorkflowModels.FindingDecision> decisionsById = uniqueById(
                decisions,
                WeeklyScrumWorkflowModels.FindingDecision::findingId,
                "finding"
        );
        requireExactIds(originals, "finding_id", decisionsById.keySet(), "finding");
        ArrayNode result = objectMapper.createArrayNode();
        originals.forEach(original -> {
            String id = requiredText(original, "finding_id");
            WeeklyScrumWorkflowModels.FindingDecision decision = decisionsById.get(id);
            String status = requireReviewStatus(decision.reviewStatus());
            validateFindingDecision(decision, status);
            ObjectNode reviewed = original.deepCopy();
            reviewed.put("review_status", status);
            putNullable(reviewed, "review_comment", decision.reviewComment());
            putNullable(reviewed, "pm_modified_title", decision.modifiedTitle());
            putNullable(reviewed, "pm_modified_description", decision.modifiedDescription());
            putNullable(reviewed, "pm_modified_action", decision.modifiedAction());
            result.add(reviewed);
        });
        return result;
    }

    private ArrayNode applyActionDecisions(
            Long projectId,
            WeeklyScrumReport report,
            ArrayNode reviewedFindings,
            ArrayNode originals,
            List<WeeklyScrumWorkflowModels.ActionDecision> decisions
    ) {
        Map<String, WeeklyScrumWorkflowModels.ActionDecision> decisionsById = uniqueById(
                decisions,
                WeeklyScrumWorkflowModels.ActionDecision::actionId,
                "action"
        );
        requireExactIds(originals, "action_id", decisionsById.keySet(), "action");
        Set<String> rejectedFindings = new LinkedHashSet<>();
        reviewedFindings.forEach(finding -> {
            if ("REJECTED".equals(finding.path("review_status").asText())) {
                rejectedFindings.add(finding.path("finding_id").asText());
            }
        });
        Set<String> activeMembers = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId)
                .stream()
                .map(member -> member.getUser().getEmployeeNumber())
                .collect(Collectors.toSet());
        LocalDate nextWeekStart = report.getWeekEndDate().plusDays(1);
        LocalDate nextWeekEnd = report.getWeekEndDate().plusDays(7);

        ArrayNode result = objectMapper.createArrayNode();
        originals.forEach(original -> {
            String id = requiredText(original, "action_id");
            WeeklyScrumWorkflowModels.ActionDecision decision = decisionsById.get(id);
            String status = requireReviewStatus(decision.reviewStatus());
            validateActionDecision(decision, status);
            ObjectNode reviewed = original.deepCopy();
            reviewed.put("review_status", status);
            putNullable(reviewed, "review_comment", decision.reviewComment());
            putNullable(reviewed, "pm_modified_title", decision.modifiedTitle());
            putNullable(reviewed, "pm_modified_owner_id", decision.modifiedOwnerId());
            putNullable(reviewed, "pm_modified_owner", decision.modifiedOwner());
            putDateNullable(reviewed, "pm_modified_due_date", decision.modifiedDueDate());
            putPriority(reviewed, decision.modifiedPriority());
            putNullable(reviewed, "pm_modified_done_condition", decision.modifiedDoneCondition());
            putNullable(reviewed, "pm_modified_reason", decision.modifiedReason());
            validateEffectiveAction(
                    reviewed,
                    status,
                    rejectedFindings,
                    activeMembers,
                    nextWeekStart,
                    nextWeekEnd
            );
            result.add(reviewed);
        });
        return result;
    }

    private void validateFindingDecision(
            WeeklyScrumWorkflowModels.FindingDecision decision,
            String status
    ) {
        if ("REJECTED".equals(status) && normalize(decision.reviewComment()) == null) {
            throw invalidReview("REJECTED finding에는 reviewComment가 필요합니다.");
        }
        if ("MODIFIED".equals(status)
                && normalize(decision.modifiedTitle()) == null
                && normalize(decision.modifiedDescription()) == null
                && normalize(decision.modifiedAction()) == null) {
            throw invalidReview("MODIFIED finding에는 수정값이 하나 이상 필요합니다.");
        }
    }

    private void validateActionDecision(
            WeeklyScrumWorkflowModels.ActionDecision decision,
            String status
    ) {
        if ("REJECTED".equals(status) && normalize(decision.reviewComment()) == null) {
            throw invalidReview("REJECTED action에는 reviewComment가 필요합니다.");
        }
        if ("MODIFIED".equals(status)
                && normalize(decision.modifiedTitle()) == null
                && normalize(decision.modifiedOwnerId()) == null
                && normalize(decision.modifiedOwner()) == null
                && decision.modifiedDueDate() == null
                && normalize(decision.modifiedPriority()) == null
                && normalize(decision.modifiedDoneCondition()) == null
                && normalize(decision.modifiedReason()) == null) {
            throw invalidReview("MODIFIED action에는 수정값이 하나 이상 필요합니다.");
        }
    }

    private void validateEffectiveAction(
            ObjectNode action,
            String reviewStatus,
            Set<String> rejectedFindingIds,
            Set<String> activeMembers,
            LocalDate nextWeekStart,
            LocalDate nextWeekEnd
    ) {
        if ("REJECTED".equals(reviewStatus) || automaticallyExcluded(action, rejectedFindingIds)) {
            return;
        }
        String ownerId = firstText(action, "pm_modified_owner_id", "owner_id");
        if (ownerId == null || !activeMembers.contains(ownerId)) {
            throw invalidReview(
                    "최종 반영 action의 담당자는 활성 프로젝트 구성원이어야 합니다. actionId="
                            + action.path("action_id").asText()
            );
        }
        LocalDate dueDate = firstDate(action, "pm_modified_due_date", "due_date");
        if (dueDate != null && (dueDate.isBefore(nextWeekStart) || dueDate.isAfter(nextWeekEnd))) {
            throw invalidReview(
                    "최종 action 기한은 다음 주 범위 안이어야 합니다. actionId="
                            + action.path("action_id").asText()
            );
        }
    }

    private boolean automaticallyExcluded(ObjectNode action, Set<String> rejectedFindingIds) {
        LinkedHashSet<String> sourceIds = new LinkedHashSet<>();
        JsonNode sourceFindingIds = action.path("source_finding_ids");
        if (sourceFindingIds.isArray()) {
            sourceFindingIds.forEach(node -> {
                if (node.isTextual()) {
                    sourceIds.add(node.asText());
                }
            });
        }
        String legacyId = textOrNull(action.get("source_finding_id"));
        if (legacyId != null) {
            sourceIds.add(legacyId);
        }
        return !sourceIds.isEmpty() && rejectedFindingIds.containsAll(sourceIds);
    }

    private <T> Map<String, T> uniqueById(
            List<T> values,
            Function<T, String> idExtractor,
            String type
    ) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = normalize(idExtractor.apply(value));
            if (id == null || result.putIfAbsent(id, value) != null) {
                throw invalidReview("중복되거나 비어 있는 " + type + " ID가 있습니다.");
            }
        }
        return result;
    }

    private void requireExactIds(
            ArrayNode originals,
            String idField,
            Set<String> requestedIds,
            String type
    ) {
        Set<String> originalIds = new LinkedHashSet<>();
        originals.forEach(node -> originalIds.add(requiredText(node, idField)));
        if (originalIds.size() != originals.size() || !originalIds.equals(requestedIds)) {
            throw invalidReview("모든 원본 " + type + "에 대한 PM 결정이 필요합니다.");
        }
    }

    private ArrayNode requireArray(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value instanceof ArrayNode arrayNode) {
            return arrayNode.deepCopy();
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_WEEKLY_SCRUM_AI_RESPONSE",
                fieldName + " 배열이 없습니다."
        );
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = textOrNull(node.get(fieldName));
        if (value != null) {
            return value;
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_WEEKLY_SCRUM_AI_RESPONSE",
                fieldName + " 값이 없습니다."
        );
    }

    private String requireReviewStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null || !REVIEW_STATUSES.contains(normalized.toUpperCase())) {
            throw invalidReview("reviewStatus는 APPROVED, MODIFIED, REJECTED 중 하나여야 합니다.");
        }
        return normalized.toUpperCase();
    }

    private void putPriority(ObjectNode node, String priority) {
        String normalized = normalize(priority);
        if (normalized == null) {
            node.putNull("pm_modified_priority");
            return;
        }
        normalized = normalized.toUpperCase();
        if (!PRIORITIES.contains(normalized)) {
            throw invalidReview("modifiedPriority는 LOW, MEDIUM, HIGH 중 하나여야 합니다.");
        }
        node.put("pm_modified_priority", normalized);
    }

    private String firstText(JsonNode node, String preferred, String fallback) {
        String value = textOrNull(node.get(preferred));
        return value == null ? textOrNull(node.get(fallback)) : value;
    }

    private LocalDate firstDate(JsonNode node, String preferred, String fallback) {
        String value = firstText(node, preferred, fallback);
        return value == null ? null : LocalDate.parse(value);
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() || !node.isValueNode()
                ? null
                : normalize(node.asText());
    }

    private void putNullable(ObjectNode node, String fieldName, String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, normalized);
        }
    }

    private void putDateNullable(ObjectNode node, String fieldName, LocalDate value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.toString());
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException invalidReview(String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_WEEKLY_SCRUM_PM_REVIEW",
                message
        );
    }
}
