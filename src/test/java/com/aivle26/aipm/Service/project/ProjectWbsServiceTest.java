package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationRequest;
import com.aivle26.aipm.Dto.project.PlanningWbsGenerationResponse;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultResponse;
import com.aivle26.aipm.Dto.project.WbsTaskResultRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectWbsResult;
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
    void generateWbsUsesNativeAgentContractAndPersistsHierarchy() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-native-001");
        Project project = projectRepository.findAll().getFirst();
        Long projectId = project.getId();
        String projectName = project.getName();
        when(planningWbsClient.generateWbs(any())).thenReturn(
                new PlanningWbsGenerationResponse(
                        projectName,
                        List.of("요구사항 분석"),
                        List.of(
                                new PlanningWbsGenerationResponse.WbsItem(
                                        1L,
                                        "1",
                                        null,
                                        1,
                                        1,
                                        "PHASE",
                                        "요구사항 분석",
                                        "요구사항 분석 단계",
                                        List.of(requirement.getId())
                                ),
                                new PlanningWbsGenerationResponse.WbsItem(
                                        2L,
                                        "1.1",
                                        1L,
                                        2,
                                        1,
                                        "WORK_PACKAGE",
                                        "인증 요구사항",
                                        "인증 요구사항 작업 묶음",
                                        List.of(requirement.getId())
                                ),
                                new PlanningWbsGenerationResponse.WbsItem(
                                        3L,
                                        "1.1.1",
                                        2L,
                                        3,
                                        1,
                                        "TASK",
                                        "문서 업로드 분석",
                                        "프로젝트 문서를 분석한다.",
                                        List.of(requirement.getId())
                                )
                        ),
                        List.of(),
                        "SUCCEEDED"
                )
        );

        var response = projectWbsService.generateWbs(projectId);

        assertThat(response.aiSuggestionTasks()).hasSize(3);
        assertThat(response.aiSuggestionTasks())
                .extracting(item -> item.externalTaskId())
                .containsExactly("WBS-1", "WBS-2", "WBS-3");
        assertThat(response.aiSuggestionTasks().get(1).parentExternalTaskId())
                .isEqualTo("WBS-1");
        assertThat(response.aiSuggestionTasks().get(2).parentExternalTaskId())
                .isEqualTo("WBS-2");
        assertThat(response.aiSuggestionTasks().get(2).phase().name())
                .isEqualTo("ANALYSIS");
        assertThat(response.aiSuggestionTasks().get(2).estimatedHours())
                .isEqualTo(24);
        assertThat(response.finalTasks()).hasSize(3);
        assertThat(projectWbsTaskRepository.findByProjectIdOrderByOrderIndexAscIdAsc(
                projectId
        )).hasSize(3);

        var requestCaptor =
                org.mockito.ArgumentCaptor.forClass(PlanningWbsGenerationRequest.class);
        org.mockito.Mockito.verify(planningWbsClient).generateWbs(requestCaptor.capture());
        PlanningWbsGenerationRequest request = requestCaptor.getValue();
        assertThat(request.projectInfo().projectName())
                .isEqualTo(projectName);
        assertThat(request.requirementCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.requirementId()).isEqualTo(requirement.getId());
            assertThat(candidate.functionName()).isEqualTo(requirement.getTitle());
            assertThat(candidate.requirementText()).isEqualTo(requirement.getDescription());
            assertThat(candidate.category()).isEqualTo(requirement.getType().name());
        });
    }

    @Test
    void generateWbsPreservesAiStatusCoverageAndTaskMetadata() {
        ProjectRequirement requirement = createConfirmedRequirement("analysis-native-metadata");
        Project project = projectRepository.findAll().getFirst();
        when(planningWbsClient.generateWbs(any())).thenReturn(
                new PlanningWbsGenerationResponse(
                        project.getName(),
                        List.of("Analysis"),
                        List.of(
                                new PlanningWbsGenerationResponse.WbsItem(
                                        1L, "1", null, 1, 1, "PHASE", "Analysis",
                                        "Analysis phase", List.of(requirement.getId())
                                ),
                                new PlanningWbsGenerationResponse.WbsItem(
                                        2L, "1.1", 1L, 2, 1, "WORK_PACKAGE", "Requirements",
                                        "Requirements package", List.of(requirement.getId())
                                ),
                                new PlanningWbsGenerationResponse.WbsItem(
                                        3L, "1.1.1", 2L, 3, 1, "TASK", "Analyze requirements",
                                        "Analyze confirmed requirements", List.of(requirement.getId()),
                                        List.of(new PlanningWbsGenerationResponse.RelatedArtifact(
                                                "WBS", "Project WBS", "1.0"
                                        )),
                                        List.of("Requirement mapping is complete")
                                )
                        ),
                        new PlanningWbsGenerationResponse.RequirementCoverage(
                                1, 1, List.of(), 100.0
                        ),
                        new PlanningWbsGenerationResponse.ArtifactCoverage(
                                1, 1, List.of(), 100.0
                        ),
                        List.of("Review the generated hierarchy"),
                        "SUCCEEDED",
                        "FALLBACK"
                )
        );

        var generated = projectWbsService.generateWbs(project.getId());
        var fetched = projectWbsService.getWbs(project.getId());

        assertThat(generated.aiStatus().llmStatus()).isEqualTo("FALLBACK");
        assertThat(generated.aiStatus().generationStatus()).isEqualTo("SUCCEEDED");
        assertThat(generated.aiStatus().warnings())
                .containsExactly("Review the generated hierarchy");
        assertThat(generated.coverage().requirements().coverageRate()).isEqualTo(100.0);
        assertThat(generated.coverage().artifacts().coverageRate()).isEqualTo(100.0);
        assertThat(generated.aiSuggestionTasks().get(2).relatedArtifacts())
                .singleElement()
                .satisfies(artifact -> assertThat(artifact.artifactType()).isEqualTo("WBS"));
        assertThat(generated.aiSuggestionTasks().get(2).completionCriteria())
                .containsExactly("Requirement mapping is complete");
        assertThat(fetched.finalTasks().get(2).relatedArtifacts())
                .singleElement()
                .satisfies(artifact -> assertThat(artifact.artifactName()).isEqualTo("Project WBS"));
        assertThat(fetched.finalTasks().get(2).completionCriteria())
                .containsExactly("Requirement mapping is complete");
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
    void saveWbsResultRegeneratesSuggestionAndPreservesFinalTasks() {
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

        projectWbsService.saveWbsResult(
                requirement.getProject().getId(),
                new SaveWbsResultRequest(
                        "wbs-20260713-002",
                        "wbs-agent-v1",
                        request.tasks()
                )
        );

        assertThat(projectWbsResultRepository.findByProjectId(requirement.getProject().getId()))
                .get()
                .extracting(ProjectWbsResult::getAgentExecutionId)
                .isEqualTo("wbs-20260713-002");
        assertThat(projectWbsTaskRepository.findByProjectIdOrderByOrderIndexAscIdAsc(
                requirement.getProject().getId()
        )).hasSize(1);
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

    private ProjectRequirement createConfirmedRequirement(String agentExecutionId) {
        Long projectId = projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project " + agentExecutionId,
                "draft description",
                null,
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
