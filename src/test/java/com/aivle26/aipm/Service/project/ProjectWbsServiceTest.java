package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.ProjectWbsResponse;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveFinalWbsRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultResponse;
import com.aivle26.aipm.Dto.project.WbsTaskResultRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.client.ai.PlanningWbsClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithMockUser(username = "PM001", roles = "PM")
class ProjectWbsServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private PlanningWbsClient planningWbsClient;

    @Autowired
    private ProjectWbsService projectWbsService;

    @Autowired
    private ProjectDocumentAnalysisService projectDocumentAnalysisService;

    @Autowired
    private ProjectDocumentService projectDocumentService;

    @Autowired
    private ProjectCreationService projectCreationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    private ProjectDocumentAnalysisResultRepository analysisResultRepository;

    @Autowired
    private ProjectRequirementRepository projectRequirementRepository;

    @Autowired
    private ProjectWbsResultRepository projectWbsResultRepository;

    @Autowired
    private ProjectWbsTaskRepository projectWbsTaskRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

    @Autowired
    private ProjectScheduleResultRepository projectScheduleResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(createPmUser("PM001"));
    }

    @Test
    void saveWbsResultSuccess() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-001");

        SaveWbsResultResponse response = projectWbsService.saveWbsResult(
                requirement.getProject().getId(),
                new SaveWbsResultRequest(
                        "wbs-20260713-001",
                        "wbs-agent-v1",
                        List.of(new WbsTaskResultRequest(
                                "TASK-001",
                                null,
                                "1",
                                "Project Analysis",
                                "Review initial documents and confirmed requirements",
                                "ANALYSIS",
                                List.of("DOCUMENT_ANALYSIS"),
                                "MEDIUM",
                                16,
                                1,
                                List.of(requirement.getId())
                        ))
                )
        );

        assertThat(response.wbsResultId()).isNotNull();
        assertThat(response.taskCount()).isEqualTo(1);
        assertThat(projectWbsResultRepository.count()).isEqualTo(1);
        var savedTask = projectWbsTaskRepository.findAll().getFirst();
        assertThat(savedTask.isConfirmed()).isFalse();
        assertThat(savedTask.getParentTask()).isNull();
    }

    @Test
    void saveWbsResultFailWhenParentMissing() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-001");

        assertThatThrownBy(() -> projectWbsService.saveWbsResult(
                requirement.getProject().getId(),
                new SaveWbsResultRequest(
                        "wbs-20260713-001",
                        "wbs-agent-v1",
                        List.of(new WbsTaskResultRequest(
                                "TASK-001",
                                "TASK-999",
                                "1",
                                "Project Analysis",
                                "Review initial documents and confirmed requirements",
                                "ANALYSIS",
                                List.of("DOCUMENT_ANALYSIS"),
                                "MEDIUM",
                                16,
                                1,
                                List.of(requirement.getId())
                        ))
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("parent task not found");

        assertThat(projectWbsResultRepository.count()).isZero();
        assertThat(projectWbsTaskRepository.count()).isZero();
    }

    @Test
    void saveWbsResultFailWhenCycleExists() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-001");

        assertThatThrownBy(() -> projectWbsService.saveWbsResult(
                requirement.getProject().getId(),
                new SaveWbsResultRequest(
                        "wbs-20260713-001",
                        "wbs-agent-v1",
                        List.of(
                                new WbsTaskResultRequest(
                                        "TASK-001",
                                        "TASK-002",
                                        "1",
                                        "Task 1",
                                        "Cycle parent link 1",
                                        "ANALYSIS",
                                        List.of("DOCUMENT_ANALYSIS"),
                                        "MEDIUM",
                                        8,
                                        1,
                                        List.of(requirement.getId())
                                ),
                                new WbsTaskResultRequest(
                                        "TASK-002",
                                        "TASK-001",
                                        "2",
                                        "Task 2",
                                        "Cycle parent link 2",
                                        "ANALYSIS",
                                        List.of("DOCUMENT_ANALYSIS"),
                                        "MEDIUM",
                                        8,
                                        2,
                                        List.of(requirement.getId())
                                )
                        )
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("wbs cycle reference");

        assertThat(projectWbsResultRepository.count()).isZero();
        assertThat(projectWbsTaskRepository.count()).isZero();
    }

    @Test
    void saveWbsResultFailWhenWbsAlreadyExists() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-001");
        SaveWbsResultRequest request = new SaveWbsResultRequest(
                "wbs-20260713-001",
                "wbs-agent-v1",
                List.of(new WbsTaskResultRequest(
                        "TASK-001",
                        null,
                        "1",
                        "Project Analysis",
                        "Review initial documents and confirmed requirements",
                        "ANALYSIS",
                        List.of("DOCUMENT_ANALYSIS"),
                        "MEDIUM",
                        16,
                        1,
                        List.of(requirement.getId())
                ))
        );

        projectWbsService.saveWbsResult(requirement.getProject().getId(), request);

        assertThatThrownBy(() -> projectWbsService.saveWbsResult(
                requirement.getProject().getId(),
                new SaveWbsResultRequest(
                        "wbs-20260713-002",
                        "wbs-agent-v1",
                        request.tasks()
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("wbs already exists");
    }

    @Test
    void saveWbsResultFailWhenRequirementBelongsToOtherProject() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-001");
        ProjectRequirement foreignRequirement = createConfirmedRequirement("analysis-002");

        assertThatThrownBy(() -> projectWbsService.saveWbsResult(
                requirement.getProject().getId(),
                new SaveWbsResultRequest(
                        "wbs-20260713-001",
                        "wbs-agent-v1",
                        List.of(new WbsTaskResultRequest(
                                "TASK-001",
                                null,
                                "1",
                                "Project Analysis",
                                "Review initial documents and confirmed requirements",
                                "ANALYSIS",
                                List.of("DOCUMENT_ANALYSIS"),
                                "MEDIUM",
                                16,
                                1,
                                List.of(requirement.getId(), foreignRequirement.getId())
                        ))
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid requirement id");

        assertThat(projectWbsResultRepository.count()).isZero();
        assertThat(projectWbsTaskRepository.count()).isZero();
    }

    @Test
    void generateWbsSendsConfirmedRequirementsAndStoresOnlyReturnedTasks() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-generate");
        when(planningWbsClient.generateWbs(any())).thenReturn(new SaveWbsResultRequest(
                "wbs-ai-generate",
                "wbs-agent-v2",
                List.of(taskRequest("AI-001", "1", "AI 생성 작업", 0, requirement.getId()))
        ));

        ProjectWbsResponse response = projectWbsService.generateWbs(requirement.getProject().getId());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(PlanningWbsGenerationRequest.class);
        verify(planningWbsClient).generateWbs(requestCaptor.capture());
        assertThat(requestCaptor.getValue().requirements()).singleElement().satisfies(sentRequirement -> {
            assertThat(sentRequirement.requirementId()).isEqualTo(requirement.getId());
            assertThat(sentRequirement.title()).isEqualTo(requirement.getTitle());
            assertThat(sentRequirement.description()).isEqualTo(requirement.getDescription());
        });
        assertThat(response.aiSuggestionTasks()).singleElement()
                .extracting(ProjectWbsResponse.WbsTaskDetail::taskName)
                .isEqualTo("AI 생성 작업");
        assertThat(response.finalTasks()).singleElement()
                .extracting(ProjectWbsResponse.WbsTaskDetail::taskName)
                .isEqualTo("AI 생성 작업");
        assertThat(response.finalConfirmed()).isFalse();
    }

    @Test
    void saveFinalWbsPreservesAiSuggestionAndRestoresEditedOrder() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-final");
        Long projectId = requirement.getProject().getId();
        projectWbsService.saveWbsResult(projectId, new SaveWbsResultRequest(
                "wbs-ai-original",
                "wbs-agent-v1",
                List.of(taskRequest("AI-001", "1", "AI 최초 작업", 0, requirement.getId()))
        ));

        ProjectWbsResponse saved = projectWbsService.saveFinalWbs(projectId, new SaveFinalWbsRequest(List.of(
                taskRequest("USER-002", "AI-001", "2", "사용자 추가 작업", 0, requirement.getId()),
                taskRequest("AI-001", "1", "사용자 수정 작업", 1, requirement.getId())
        )));
        ProjectWbsResponse restored = projectWbsService.getWbs(projectId);

        assertThat(saved.finalConfirmed()).isTrue();
        assertThat(restored.aiSuggestionTasks()).extracting(ProjectWbsResponse.WbsTaskDetail::taskName)
                .containsExactly("AI 최초 작업");
        assertThat(restored.finalTasks()).extracting(ProjectWbsResponse.WbsTaskDetail::taskName)
                .containsExactly("사용자 추가 작업", "사용자 수정 작업");
        assertThat(restored.finalTasks().getFirst().parentExternalTaskId()).isEqualTo("AI-001");
        assertThat(restored.finalTasks()).allSatisfy(task -> assertThat(task.confirmed()).isTrue());
    }

    @Test
    void saveFinalWbsRejectsRequirementFromAnotherProject() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-final-owner");
        ProjectRequirement foreignRequirement = createConfirmedRequirement("analysis-final-foreign");
        Long projectId = requirement.getProject().getId();
        projectWbsService.saveWbsResult(projectId, new SaveWbsResultRequest(
                "wbs-ai-owner",
                "wbs-agent-v1",
                List.of(taskRequest("AI-001", "1", "AI 최초 작업", 0, requirement.getId()))
        ));

        assertThatThrownBy(() -> projectWbsService.saveFinalWbs(projectId, new SaveFinalWbsRequest(
                List.of(taskRequest("USER-001", "1", "잘못된 연결", 0, foreignRequirement.getId()))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid requirement id");

        assertThat(projectWbsService.getWbs(projectId).finalTasks())
                .extracting(ProjectWbsResponse.WbsTaskDetail::taskName)
                .containsExactly("AI 최초 작업");
    }

    private WbsTaskResultRequest taskRequest(
            String externalTaskId,
            String taskCode,
            String taskName,
            int orderIndex,
            Long requirementId
    ) {
        return taskRequest(externalTaskId, null, taskCode, taskName, orderIndex, requirementId);
    }

    private WbsTaskResultRequest taskRequest(
            String externalTaskId,
            String parentExternalTaskId,
            String taskCode,
            String taskName,
            int orderIndex,
            Long requirementId
    ) {
        return new WbsTaskResultRequest(
                externalTaskId,
                parentExternalTaskId,
                taskCode,
                taskName,
                taskName + " 설명",
                "ANALYSIS",
                List.of("REQUIREMENTS_ANALYSIS"),
                "MEDIUM",
                8,
                orderIndex,
                List.of(requirementId)
        );
    }

    private ProjectRequirement createConfirmedRequirement(String agentExecutionId) {
        Long projectId = projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project " + agentExecutionId,
                "draft description",
                "PM001",
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        )).projectId();

        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes()))
        );

        Long documentId = projectDocumentRepository.findByProjectId(projectId).getFirst().getId();
        projectDocumentAnalysisService.saveAnalysisResult(
                projectId,
                new SaveDocumentAnalysisResultRequest(
                        agentExecutionId,
                        "document-agent-v1",
                        "Build PM platform",
                        "Automate project setup",
                        List.of(new DocumentAnalysisRequirementRequest(
                                1L,
                                "FUNCTIONAL",
                                "Upload project documents",
                                "PM can upload initial documents.",
                                "HIGH",
                                documentId
                        )),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode()
                )
        );

        ProjectRequirement requirement = projectRequirementRepository.findByProjectIdAndStatus(projectId, RequirementStatus.UNCONFIRMED).getFirst();
        requirement.setStatus(RequirementStatus.CONFIRMED);
        return projectRequirementRepository.save(requirement);
    }

    private User createPmUser(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Project Manager");
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setPassword("encoded-password");
        user.setRole("PM");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }
}
