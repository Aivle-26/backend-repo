package com.aivle26.aipm.Service.project.weekly;

import com.aivle26.aipm.Dto.project.SaveWeeklyScrumRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import com.aivle26.aipm.Entity.project.TaskProgressStatus;
import com.aivle26.aipm.Entity.project.WeeklyScrumReport;
import com.aivle26.aipm.Entity.project.WeeklyScrumSubmission;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectTaskAssignmentRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumSubmissionRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WeeklyScrumAnalysisAssembler {
    private static final Set<String> TASK_TYPES = Set.of(
            "PLANNING", "FRONTEND", "BACKEND", "AI", "DATA", "QA",
            "DEVOPS", "DOCUMENT", "OTHER"
    );
    private static final Set<String> ACTION_STATUSES =
            Set.of("TODO", "IN_PROGRESS", "DONE", "BLOCKED");
    private static final Set<String> SOURCE_TYPES = Set.of(
            "WEEKLY_SCRUM", "JIRA_ISSUE", "GITHUB_ISSUE", "GITHUB_PR",
            "SLACK_MESSAGE", "MEETING_ACTION", "WBS_TASK", "REQUIREMENT",
            "DELIVERABLE", "OTHER"
    );

    private final ProjectMemberRepository projectMemberRepository;
    private final WeeklyScrumSubmissionRepository submissionRepository;
    private final ProjectTaskAssignmentRepository assignmentRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final UserCapabilityProfileRepository capabilityProfileRepository;
    private final ObjectMapper objectMapper;

    public ObjectNode buildSummarizeRequest(WeeklyScrumReport report) {
        AnalysisContext context = loadContext(report.getProject(), report.getWeekStartDate());
        ObjectNode request = objectMapper.createObjectNode();
        addCommon(request, report, report.getProject());
        putIfNotBlank(request, "sprint_goal", report.getSprintGoal());
        ArrayNode expectedMembers = request.putArray("expected_members");
        context.members().forEach(member -> expectedMembers.add(member.getUser().getName()));
        ArrayNode teamMembers = request.putArray("team_members");
        context.members().forEach(member -> teamMembers.add(toTeamMember(member, context)));
        ArrayNode updates = request.putArray("member_updates");
        context.submissions().forEach(submission -> updates.add(toMemberUpdate(submission, context)));
        request.put("enable_llm", report.isEnableLlm());
        return request;
    }

    public ObjectNode buildReviewRequest(WeeklyScrumReport report, JsonNode summarize) {
        Project project = report.getProject();
        ObjectNode request = objectMapper.createObjectNode();
        addCommon(request, report, project);
        putIfNotBlank(request, "sprint_goal", report.getSprintGoal());
        request.set("fact_summary", requireObject(summarize, "fact_summary"));
        request.set("team_summary", requireObject(summarize, "team_summary"));
        ArrayNode references = request.putArray("reference_documents");
        requirementRepository.findAllByFilters(project.getId(), null, null, null, true)
                .stream()
                .filter(ProjectRequirement::isIncludedInFinal)
                .forEach(requirement -> references.add(toReferenceDocument(requirement)));
        request.put("analysis_date", report.getWeekEndDate().toString());
        request.put("enable_llm", report.isEnableLlm());
        return request;
    }

    public ObjectNode buildRecommendRequest(
            WeeklyScrumReport report,
            JsonNode summarize,
            JsonNode review
    ) {
        ObjectNode request = objectMapper.createObjectNode();
        addCommon(request, report, report.getProject());
        request.set("fact_summary", requireObject(summarize, "fact_summary"));
        request.set("team_summary", requireObject(summarize, "team_summary"));
        request.set("review_findings", requireArray(review, "review_findings"));
        request.put("next_week_start", report.getWeekEndDate().plusDays(1).toString());
        request.put("next_week_end", report.getWeekEndDate().plusDays(7).toString());
        request.put("enable_llm", report.isEnableLlm());
        return request;
    }

    private AnalysisContext loadContext(Project project, LocalDate weekStartDate) {
        Long projectId = project.getId();
        List<ProjectMember> members = projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(projectId);
        if (members.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_PROJECT_MEMBER_NOT_FOUND",
                    "활성 프로젝트 구성원이 없습니다."
            );
        }
        Map<String, ProjectMember> membersByEmployeeNumber = members.stream()
                .collect(Collectors.toMap(
                        member -> member.getUser().getEmployeeNumber(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<WeeklyScrumSubmission> submissions = submissionRepository
                .findByProjectIdAndWeekStartDateOrderByUpdatedAtDescEmployeeNumberAsc(
                        projectId,
                        weekStartDate
                ).stream()
                .filter(submission -> membersByEmployeeNumber.containsKey(
                        submission.getEmployeeNumber()
                ))
                .toList();
        if (submissions.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WEEKLY_SCRUM_SUBMISSION_NOT_FOUND",
                    "분석할 주간 스크럼 제출 내역이 없습니다."
            );
        }
        Map<String, UserCapabilityProfile> profiles = capabilityProfileRepository
                .findAllByEmployeeNumberIn(membersByEmployeeNumber.keySet())
                .stream()
                .collect(Collectors.toMap(
                        UserCapabilityProfile::getEmployeeNumber,
                        Function.identity()
                ));
        Map<String, Integer> workloads = assignmentRepository
                .findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(projectId)
                .stream()
                .filter(assignment -> assignment.getStatus() != TaskProgressStatus.COMPLETED)
                .collect(Collectors.groupingBy(
                        ProjectTaskAssignment::getEmployeeNumber,
                        Collectors.summingInt(assignment -> assignment.getWbsTask().getEstimatedHours())
                ));
        return new AnalysisContext(
                members,
                membersByEmployeeNumber,
                submissions,
                profiles,
                workloads
        );
    }

    private ObjectNode toTeamMember(ProjectMember member, AnalysisContext context) {
        String employeeNumber = member.getUser().getEmployeeNumber();
        UserCapabilityProfile profile = context.profiles().get(employeeNumber);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("member_id", employeeNumber);
        result.put("member_name", member.getUser().getName());
        result.put("role", professionalRole(profile));
        ArrayNode skills = result.putArray("skills");
        if (profile != null) {
            profile.getSkills().forEach(skill -> skills.add(skill.getSkillCode()));
        }
        result.put("availability_hours", member.getAvailableHoursPerWeek());
        result.put("current_workload_hours", context.workloads().getOrDefault(employeeNumber, 0));
        return result;
    }

    private ObjectNode toMemberUpdate(
            WeeklyScrumSubmission submission,
            AnalysisContext context
    ) {
        ProjectMember member = context.membersByEmployeeNumber().get(submission.getEmployeeNumber());
        String role = professionalRole(context.profiles().get(submission.getEmployeeNumber()));
        SaveWeeklyScrumRequest.Details details = readDetails(submission.getDetailsJson());
        ObjectNode update = objectMapper.createObjectNode();
        update.put("member_id", submission.getEmployeeNumber());
        update.put("member_name", member.getUser().getName());
        update.put("role", role);
        addStrings(
                update.putArray("weekly_goal"),
                details == null || details.weeklyGoals() == null
                        ? List.of(submission.getPlannedWork())
                        : details.weeklyGoals()
        );
        addItems(update, "completed_tasks", details == null ? null : details.completedTasks(),
                fallbackItem(submission, "COMPLETED", submission.getCompletedWork(), "DONE"), member, role);
        addItems(update, "in_progress_tasks", details == null ? null : details.inProgressTasks(),
                null, member, role);
        addItems(update, "delayed_tasks", details == null ? null : details.delayedTasks(),
                null, member, role);
        addItems(update, "issues", details == null ? null : details.issues(),
                submission.getBlockers() == null ? null
                        : fallbackItem(submission, "BLOCKER", submission.getBlockers(), "BLOCKED"),
                member, role);
        addItems(update, "reported_risks", details == null ? null : details.reportedRisks(),
                null, member, role);
        addItems(update, "next_week_tasks", details == null ? null : details.nextWeekTasks(),
                fallbackItem(submission, "PLAN", submission.getPlannedWork(), "TODO"), member, role);
        addItems(update, "requests", details == null ? null : details.requests(),
                null, member, role);
        return update;
    }

    private void addItems(
            ObjectNode update,
            String fieldName,
            List<SaveWeeklyScrumRequest.ScrumItem> configured,
            SaveWeeklyScrumRequest.ScrumItem fallback,
            ProjectMember member,
            String role
    ) {
        ArrayNode array = update.putArray(fieldName);
        List<SaveWeeklyScrumRequest.ScrumItem> items = configured == null
                ? fallback == null ? List.of() : List.of(fallback)
                : configured;
        items.forEach(item -> array.add(toScrumItem(item, member, role)));
    }

    private ObjectNode toScrumItem(
            SaveWeeklyScrumRequest.ScrumItem item,
            ProjectMember member,
            String role
    ) {
        ObjectNode result = objectMapper.createObjectNode();
        putIfNotBlank(result, "item_id", item.itemId());
        result.put("title", item.title().trim());
        putIfNotBlank(result, "description", item.description());
        result.put("task_type", allowedOrDefault(item.taskType(), TASK_TYPES, "OTHER", "taskType"));
        result.put("owner_id", defaultIfBlank(item.ownerId(), member.getUser().getEmployeeNumber()));
        result.put("owner", defaultIfBlank(item.owner(), member.getUser().getName()));
        putDate(result, "due_date", item.dueDate());
        if (item.status() != null) {
            result.put("status", allowedOrDefault(item.status(), ACTION_STATUSES, null, "status"));
        }
        putIfNotBlank(result, "evidence_text", item.evidenceText());
        putIfNotBlank(result, "done_condition", item.doneCondition());
        if (item.estimatedHours() != null) {
            result.put("estimated_hours", item.estimatedHours());
        }
        result.put("carryover_count", item.carryoverCount() == null ? 0 : item.carryoverCount());
        addStrings(result.putArray("dependency_ids"), safeList(item.dependencyIds()));
        addStrings(result.putArray("related_task_ids"), safeList(item.relatedTaskIds()));
        result.put("integration_required", Boolean.TRUE.equals(item.integrationRequired()));
        result.put("source_type", allowedOrDefault(
                item.sourceType(), SOURCE_TYPES, "WEEKLY_SCRUM", "sourceType"
        ));
        putIfNotBlank(result, "source_reference_id", item.sourceReferenceId());
        putIfNotBlank(result, "requirement_id", item.requirementId());
        putIfNotBlank(result, "wbs_id", item.wbsId());
        putIfNotBlank(result, "deliverable_id", item.deliverableId());
        result.put("source_member_id", member.getUser().getEmployeeNumber());
        result.put("source_member_name", member.getUser().getName());
        result.put("source_member_role", role);
        return result;
    }

    private SaveWeeklyScrumRequest.ScrumItem fallbackItem(
            WeeklyScrumSubmission submission,
            String suffix,
            String title,
            String status
    ) {
        return new SaveWeeklyScrumRequest.ScrumItem(
                "SCRUM-" + submission.getId() + '-' + suffix,
                title, null, "OTHER", submission.getEmployeeNumber(), null, null,
                status, null, null, null, 0, List.of(), List.of(), false,
                "WEEKLY_SCRUM", String.valueOf(submission.getId()), null, null, null
        );
    }

    private ObjectNode toReferenceDocument(ProjectRequirement requirement) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("document_id", "REQ-" + requirement.getId());
        result.put("document_type", "REQUIREMENT_SPEC");
        result.put("title", requirement.getTitle());
        StringBuilder content = new StringBuilder(requirement.getDescription());
        if (requirement.getAcceptanceCriteria() != null) {
            content.append("\nAcceptance criteria: ").append(requirement.getAcceptanceCriteria());
        }
        if (requirement.getDueDate() != null) {
            content.append("\nDue date: ").append(requirement.getDueDate());
        }
        result.put("content", content.toString());
        return result;
    }

    private SaveWeeklyScrumRequest.Details readDetails(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, SaveWeeklyScrumRequest.Details.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_WEEKLY_SCRUM_DETAILS",
                    "저장된 주간 스크럼 상세 정보 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }

    private void addCommon(ObjectNode request, WeeklyScrumReport report, Project project) {
        request.put("project_id", project.getId());
        request.put("project_name", project.getName());
        request.put("week_start", report.getWeekStartDate().toString());
        request.put("week_end", report.getWeekEndDate().toString());
    }

    private ObjectNode requireObject(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        throw invalidResponse(fieldName + " 객체가 없습니다.");
    }

    private ArrayNode requireArray(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value instanceof ArrayNode arrayNode) {
            return arrayNode.deepCopy();
        }
        throw invalidResponse(fieldName + " 배열이 없습니다.");
    }

    private String allowedOrDefault(
            String value,
            Set<String> allowed,
            String defaultValue,
            String fieldName
    ) {
        String normalized = normalize(value);
        if (normalized == null) {
            return defaultValue;
        }
        normalized = normalized.toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_WEEKLY_SCRUM_ITEM",
                    fieldName + " 값이 올바르지 않습니다. value=" + value
            );
        }
        return normalized;
    }

    private String professionalRole(UserCapabilityProfile profile) {
        return profile == null || profile.getRoles().isEmpty()
                ? "STAFF"
                : profile.getRoles().stream().sorted().findFirst().orElse("STAFF");
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private void putDate(ObjectNode node, String fieldName, LocalDate value) {
        if (value != null) {
            node.put(fieldName, value.toString());
        }
    }

    private void putIfNotBlank(ObjectNode node, String fieldName, String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            node.put(fieldName, normalized);
        }
    }

    private void addStrings(ArrayNode array, List<String> values) {
        values.stream().map(this::normalize).filter(value -> value != null).forEach(array::add);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApiException invalidResponse(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_WEEKLY_SCRUM_AI_RESPONSE",
                message
        );
    }

    private record AnalysisContext(
            List<ProjectMember> members,
            Map<String, ProjectMember> membersByEmployeeNumber,
            List<WeeklyScrumSubmission> submissions,
            Map<String, UserCapabilityProfile> profiles,
            Map<String, Integer> workloads
    ) {
    }
}
