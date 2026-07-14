package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.SaveWbsResultResponse;
import com.aivle26.aipm.Dto.WbsTaskResultRequest;
import com.aivle26.aipm.Entity.ProjectRequirement;
import com.aivle26.aipm.Entity.RequirementStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProjectWbsServiceTest {

    @Autowired
    private ProjectWbsService projectWbsService;

    @Autowired
    private ProjectDocumentAnalysisService projectDocumentAnalysisService;

    @Autowired
    private ProjectDocumentService projectDocumentService;

    @Autowired
    private ProjectService projectService;

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

    private ProjectRequirement createConfirmedRequirement(String agentExecutionId) {
        Long projectId = projectService.createProjectDraft(new CreateProjectDraftRequest(
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
                                "REQ-" + agentExecutionId,
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
