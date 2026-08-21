package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.DeliverableRagRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.client.ai.ProjectAssistantClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectAssistantService {

    private static final int MAX_DOCUMENTS = 100;

    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository requirementRepository;
    private final ProjectWbsTaskRepository wbsTaskRepository;
    private final ProjectScheduleRepository scheduleRepository;
    private final ProjectAssistantClient assistantClient;

    public ProjectAssistantQueryResponse query(Long projectId, ProjectAssistantQueryRequest request) {
        projectAuthorizationService.requireProjectAccess(projectId);
        Project project = projectRepository.findById(projectId).orElseThrow();
        List<DeliverableRagRequest.Document> documents = buildKnowledgeDocuments(project);
        return assistantClient.query(new DeliverableRagRequest(
                projectId,
                request.question().trim(),
                documents,
                request.isLlmEnabled()
        ));
    }

    private List<DeliverableRagRequest.Document> buildKnowledgeDocuments(Project project) {
        List<DeliverableRagRequest.Document> documents = new ArrayList<>();
        documents.add(document("project", "project-" + project.getId(), "프로젝트 개요",
                projectText(project), null, null, null));

        for (ProjectRequirement requirement : requirementRepository.findByProjectIdOrderByIdAsc(project.getId())) {
            if (!requirement.isIncludedInFinal() || documents.size() >= MAX_DOCUMENTS) continue;
            documents.add(document(
                    valueOrDefault(requirement.getDeliverableName(), "requirement"),
                    "requirement-" + requirement.getId(),
                    "요구사항: " + requirement.getTitle(),
                    requirementText(requirement),
                    requirement.getId(), null,
                    requirement.isConfirmed() ? "APPROVED" : "PENDING"
            ));
        }

        for (ProjectWbsTask task : wbsTaskRepository.findByProjectIdOrderByOrderIndexAscIdAsc(project.getId())) {
            if (documents.size() >= MAX_DOCUMENTS) break;
            documents.add(document("wbs", "wbs-" + task.getId(), "WBS: " + task.getTaskName(),
                    wbsText(task), null, task.getId(), task.isConfirmed() ? "APPROVED" : "PENDING"));
        }

        for (ProjectSchedule schedule : scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(project.getId())) {
            if (documents.size() >= MAX_DOCUMENTS) break;
            documents.add(document("schedule", "schedule-" + schedule.getId(),
                    "일정: " + schedule.getWbsTask().getTaskName(), scheduleText(schedule),
                    null, schedule.getWbsTask().getId(), schedule.isConfirmed() ? "APPROVED" : "PENDING"));
        }
        return List.copyOf(documents);
    }

    private DeliverableRagRequest.Document document(String deliverableId, String documentId,
                                                      String name, String text, Long requirementId,
                                                      Long wbsId, String reviewStatus) {
        return new DeliverableRagRequest.Document(deliverableId, documentId, name, text,
                null, requirementId, wbsId, reviewStatus);
    }

    private String projectText(Project project) {
        return String.join("\n",
                "프로젝트명: " + project.getName(),
                "설명: " + valueOrDefault(project.getDescription(), "없음"),
                "고객사: " + valueOrDefault(project.getClientOrganization(), "없음"),
                "상태: " + project.getStatus(),
                "계획 시작일: " + Objects.toString(project.getPlannedStartDate(), "미정"),
                "계획 종료일: " + Objects.toString(project.getPlannedEndDate(), "미정"));
    }

    private String requirementText(ProjectRequirement requirement) {
        return String.join("\n",
                "제목: " + requirement.getTitle(),
                "설명: " + requirement.getDescription(),
                "인수 조건: " + valueOrDefault(requirement.getAcceptanceCriteria(), "없음"),
                "유형: " + requirement.getType(),
                "우선순위: " + requirement.getPriority(),
                "상태: " + requirement.getStatus(),
                "마감일: " + Objects.toString(requirement.getDueDate(), "미정"));
    }

    private String wbsText(ProjectWbsTask task) {
        return String.join("\n",
                "작업 코드: " + task.getTaskCode(),
                "작업명: " + task.getTaskName(),
                "설명: " + task.getDescription(),
                "단계: " + task.getPhase(),
                "난이도: " + task.getDifficulty(),
                "예상 시간: " + task.getEstimatedHours() + "시간",
                "필요 기술: " + task.getRequiredSkills());
    }

    private String scheduleText(ProjectSchedule schedule) {
        return String.join("\n",
                "WBS 작업: " + schedule.getWbsTask().getTaskName(),
                "시작일: " + schedule.getStartDate(),
                "종료일: " + schedule.getEndDate(),
                "예상 일수: " + schedule.getEstimatedDays(),
                "마일스톤: " + schedule.isMilestone(),
                "버퍼 일수: " + schedule.getBufferDays());
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
