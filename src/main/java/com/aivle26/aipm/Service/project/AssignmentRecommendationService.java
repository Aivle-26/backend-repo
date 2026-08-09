package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignmentRecommendationRequest;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentRecommendationService {

    private final ProjectAuthorizationService projectAuthorizationService;
    private final PlanningResourceContextAssembler contextAssembler;
    private final PlanningResourceClient planningResourceClient;

    @Transactional(readOnly = true)
    public AssignmentRecommendationResponse recommend(
            Long projectId,
            AssignmentRecommendationRequest request
    ) {
        projectAuthorizationService.requireProjectPm(projectId);
        PlanningResourceContextAssembler.PlanningResourceContext context =
                contextAssembler.assembleForRecommendation(projectId, request);
        PlanningResourceRecommendResponse aiResponse =
                planningResourceClient.recommendAssignments(context.aiRequest());
        return toFrontendResponse(context, aiResponse);
    }

    private AssignmentRecommendationResponse toFrontendResponse(
            PlanningResourceContextAssembler.PlanningResourceContext context,
            PlanningResourceRecommendResponse aiResponse
    ) {
        validateAiResponse(context, aiResponse);
        Map<Long, ProjectWbsTask> tasksById = context.tasks().stream()
                .map(PlanningResourceContextAssembler.TaskContext::task)
                .collect(Collectors.toMap(ProjectWbsTask::getId, Function.identity()));

        List<AssignmentRecommendationResponse.Assignment> assignments =
                aiResponse.assignments().stream()
                        .map(assignment -> toFrontendAssignment(
                                assignment,
                                tasksById.get(assignment.wbsId()),
                                context.candidatesByAiId()
                        ))
                        .toList();
        List<AssignmentRecommendationResponse.Candidate> candidates =
                context.candidatesByAiId().values().stream()
                        .map(candidate -> {
                            User user = candidate.user();
                            return new AssignmentRecommendationResponse.Candidate(
                                    user.getEmployeeNumber(),
                                    user.getName(),
                                    user.getEmail(),
                                    candidate.availableHoursPerWeek()
                            );
                        })
                        .toList();

        return new AssignmentRecommendationResponse(
                context.project().getId(),
                context.candidateMode(),
                candidates,
                aiResponse.requiredStaffing(),
                assignments,
                aiResponse.totalEstimatedPersonDays(),
                aiResponse.totalEstimatedHours(),
                aiResponse.totalEstimatedMm(),
                aiResponse.unassignedWbsIds(),
                withExcludedCandidateWarning(context, aiResponse.warnings()),
                aiResponse.llmStatus()
        );
    }

    /**
     * 역량(역할) 미등록으로 후보에서 제외된 팀원을 warnings에 덧붙인다.
     * 이 사실을 알리지 않으면 PM 입장에선 "추천이 계속 같은 사람만 나온다"로만 보인다.
     */
    private List<String> withExcludedCandidateWarning(
            PlanningResourceContextAssembler.PlanningResourceContext context,
            List<String> aiWarnings
    ) {
        List<String> excluded = context.excludedCandidateNames();
        if (excluded == null || excluded.isEmpty()) {
            return aiWarnings;
        }
        List<String> warnings = new ArrayList<>(aiWarnings);
        warnings.add(
                "역량이 등록되지 않아 추천 후보에서 제외된 팀원: "
                        + String.join(", ", excluded)
                        + " (팀원 역량을 등록하면 추천 대상에 포함됩니다.)"
        );
        return List.copyOf(warnings);
    }

    private AssignmentRecommendationResponse.Assignment toFrontendAssignment(
            PlanningResourceRecommendResponse.Assignment assignment,
            ProjectWbsTask task,
            Map<Long, PlanningResourceContextAssembler.CandidateContext> candidatesByAiId
    ) {
        List<AssignmentRecommendationResponse.RecommendedMember> recommendedMembers =
                assignment.recommendedMembers().stream()
                        .map(recommended -> {
                            User user = candidatesByAiId
                                    .get(recommended.projectMemberId())
                                    .user();
                            return new AssignmentRecommendationResponse.RecommendedMember(
                                    user.getEmployeeNumber(),
                                    user.getName(),
                                    user.getEmail(),
                                    recommended.recommendationScore(),
                                    recommended.assignedHours(),
                                    recommended.remainingAvailableHours()
                            );
                        })
                        .toList();
        return new AssignmentRecommendationResponse.Assignment(
                assignment.wbsId(),
                task.getTaskName(),
                assignment.requiredRoleCode(),
                assignment.requiredSkills(),
                assignment.estimatedPersonDays(),
                assignment.estimatedHours(),
                assignment.estimatedMm(),
                assignment.requiredHeadcount(),
                recommendedMembers,
                assignment.recommendationReason()
        );
    }

    private void validateAiResponse(
            PlanningResourceContextAssembler.PlanningResourceContext context,
            PlanningResourceRecommendResponse response
    ) {
        Project project = context.project();
        if (response == null || !project.getId().equals(response.projectId())) {
            throw invalidAiResponse();
        }
        if (response.requiredStaffing() == null
                || response.assignments() == null
                || response.unassignedWbsIds() == null
                || response.warnings() == null) {
            throw invalidAiResponse();
        }
        Set<Long> taskIds = context.tasks().stream()
                .map(task -> task.task().getId())
                .collect(Collectors.toSet());
        Set<Long> assignmentIds = new LinkedHashSet<>();
        for (PlanningResourceRecommendResponse.Assignment assignment : response.assignments()) {
            if (assignment == null
                    || !taskIds.contains(assignment.wbsId())
                    || !assignmentIds.add(assignment.wbsId())
                    || assignment.requiredSkills() == null
                    || assignment.recommendedMembers() == null) {
                throw invalidAiResponse();
            }
            for (PlanningResourceRecommendResponse.RecommendedMember member
                    : assignment.recommendedMembers()) {
                if (member == null
                        || !context.candidatesByAiId().containsKey(member.projectMemberId())) {
                    throw invalidAiResponse();
                }
            }
        }
        if (response.unassignedWbsIds().stream().anyMatch(id -> !taskIds.contains(id))) {
            throw invalidAiResponse();
        }
    }

    private ApiException invalidAiResponse() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_PLANNING_RESOURCE_RESPONSE",
                "The AI assignment recommendation response is invalid."
        );
    }
}
