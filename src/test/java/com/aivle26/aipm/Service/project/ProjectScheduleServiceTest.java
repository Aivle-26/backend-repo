package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveScheduleResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.project.ScheduleResultRequest;
import com.aivle26.aipm.Dto.project.WbsTaskResultRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
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
class ProjectScheduleServiceTest {

    @Autowired
    private ProjectScheduleService projectScheduleService;

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
    private ProjectScheduleResultRepository projectScheduleResultRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

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
    void saveScheduleResultSuccess() {
        List<ProjectWbsTask> wbsTasks = createConfirmedWbs("analysis-001", "wbs-001");

        var response = projectScheduleService.saveScheduleResult(
                wbsTasks.getFirst().getProject().getId(),
                new SaveScheduleResultRequest(
                        "schedule-20260713-001",
                        "schedule-agent-v1",
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 31),
                        List.of(
                                new ScheduleResultRequest(
                                        "SCH-001",
                                        wbsTasks.getFirst().getId(),
                                        LocalDate.of(2026, 7, 20),
                                        LocalDate.of(2026, 7, 22),
                                        3,
                                        List.of(),
                                        false,
                                        0
                                ),
                                new ScheduleResultRequest(
                                        "SCH-002",
                                        wbsTasks.getLast().getId(),
                                        LocalDate.of(2026, 7, 23),
                                        LocalDate.of(2026, 7, 24),
                                        2,
                                        List.of(wbsTasks.getFirst().getId()),
                                        false,
                                        0
                                )
                        )
                )
        );

        assertThat(response.scheduleResultId()).isNotNull();
        assertThat(response.scheduleCount()).isEqualTo(2);
        assertThat(projectScheduleResultRepository.count()).isEqualTo(1);
        assertThat(projectScheduleRepository.count()).isEqualTo(2);
        assertThat(projectScheduleRepository.findAll().getFirst().isConfirmed()).isFalse();
    }

    @Test
    void saveScheduleResultFailWhenDateInvalid() {
        List<ProjectWbsTask> wbsTasks = createConfirmedWbs("analysis-001", "wbs-001");

        assertThatThrownBy(() -> projectScheduleService.saveScheduleResult(
                wbsTasks.getFirst().getProject().getId(),
                new SaveScheduleResultRequest(
                        "schedule-20260713-001",
                        "schedule-agent-v1",
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 31),
                        List.of(new ScheduleResultRequest(
                                "SCH-001",
                                wbsTasks.getFirst().getId(),
                                LocalDate.of(2026, 7, 22),
                                LocalDate.of(2026, 7, 20),
                                3,
                                List.of(),
                                false,
                                0
                        ))
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid schedule date");
    }

    @Test
    void saveScheduleResultFailWhenWbsIdInvalid() {
        List<ProjectWbsTask> wbsTasks = createConfirmedWbs("analysis-001", "wbs-001");

        assertThatThrownBy(() -> projectScheduleService.saveScheduleResult(
                wbsTasks.getFirst().getProject().getId(),
                new SaveScheduleResultRequest(
                        "schedule-20260713-001",
                        "schedule-agent-v1",
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 31),
                        List.of(new ScheduleResultRequest(
                                "SCH-001",
                                99999L,
                                LocalDate.of(2026, 7, 20),
                                LocalDate.of(2026, 7, 22),
                                3,
                                List.of(),
                                false,
                                0
                        ))
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid wbs id");

        assertThat(projectScheduleResultRepository.count()).isZero();
        assertThat(projectScheduleRepository.count()).isZero();
    }

    @Test
    void saveScheduleResultFailWhenScheduleAlreadyExists() {
        List<ProjectWbsTask> wbsTasks = createConfirmedWbs("analysis-001", "wbs-001");
        SaveScheduleResultRequest request = new SaveScheduleResultRequest(
                "schedule-20260713-001",
                "schedule-agent-v1",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 31),
                List.of(new ScheduleResultRequest(
                        "SCH-001",
                        wbsTasks.getFirst().getId(),
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 22),
                        3,
                        List.of(),
                        false,
                        0
                ))
        );

        projectScheduleService.saveScheduleResult(wbsTasks.getFirst().getProject().getId(), request);

        assertThatThrownBy(() -> projectScheduleService.saveScheduleResult(
                wbsTasks.getFirst().getProject().getId(),
                new SaveScheduleResultRequest(
                        "schedule-20260713-002",
                        "schedule-agent-v1",
                        request.projectStartDate(),
                        request.targetEndDate(),
                        request.schedules()
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("schedule already exists");
    }

    @Test
    void saveScheduleResultRollbackWhenPredecessorInvalid() {
        List<ProjectWbsTask> wbsTasks = createConfirmedWbs("analysis-001", "wbs-001");

        assertThatThrownBy(() -> projectScheduleService.saveScheduleResult(
                wbsTasks.getFirst().getProject().getId(),
                new SaveScheduleResultRequest(
                        "schedule-20260713-001",
                        "schedule-agent-v1",
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 31),
                        List.of(
                                new ScheduleResultRequest(
                                        "SCH-001",
                                        wbsTasks.getFirst().getId(),
                                        LocalDate.of(2026, 7, 20),
                                        LocalDate.of(2026, 7, 22),
                                        3,
                                        List.of(),
                                        false,
                                        0
                                ),
                                new ScheduleResultRequest(
                                        "SCH-002",
                                        wbsTasks.getLast().getId(),
                                        LocalDate.of(2026, 7, 23),
                                        LocalDate.of(2026, 7, 24),
                                        2,
                                        List.of(99999L),
                                        false,
                                        0
                                )
                        )
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("predecessor wbs not found");

        assertThat(projectScheduleResultRepository.count()).isZero();
        assertThat(projectScheduleRepository.count()).isZero();
    }

    private List<ProjectWbsTask> createConfirmedWbs(String analysisExecutionId, String wbsExecutionId) {
        Long projectId = projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project " + analysisExecutionId,
                "draft description",
                "PM001",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 10, 31)
        )).projectId();

        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes()))
        );

        Long documentId = projectDocumentRepository.findByProjectId(projectId).getFirst().getId();
        projectDocumentAnalysisService.saveAnalysisResult(
                projectId,
                new SaveDocumentAnalysisResultRequest(
                        analysisExecutionId,
                        "document-agent-v1",
                        "Build PM platform",
                        "Automate project setup",
                        List.of(
                                new DocumentAnalysisRequirementRequest(
                                        1L,
                                        "FUNCTIONAL",
                                        "Upload project documents",
                                        "PM can upload initial documents.",
                                        "HIGH",
                                        documentId
                                ),
                                new DocumentAnalysisRequirementRequest(
                                        2L,
                                        "FUNCTIONAL",
                                        "Review project requirements",
                                        "PM can review extracted requirements.",
                                        "HIGH",
                                        documentId
                                )
                        ),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode()
                )
        );

        var requirements = projectRequirementRepository.findByProjectIdAndStatus(projectId, RequirementStatus.UNCONFIRMED);
        for (var requirement : requirements) {
            requirement.setStatus(RequirementStatus.CONFIRMED);
        }
        projectRequirementRepository.saveAll(requirements);

        projectWbsService.saveWbsResult(
                projectId,
                new SaveWbsResultRequest(
                        wbsExecutionId,
                        "wbs-agent-v1",
                        List.of(
                                new WbsTaskResultRequest(
                                        "TASK-001",
                                        null,
                                        "1",
                                        "Project Analysis",
                                        "Review documents",
                                        "ANALYSIS",
                                        List.of("DOCUMENT_ANALYSIS"),
                                        "MEDIUM",
                                        16,
                                        1,
                                        List.of(requirements.get(0).getId())
                                ),
                                new WbsTaskResultRequest(
                                        "TASK-002",
                                        null,
                                        "2",
                                        "Requirement Review",
                                        "Review requirements",
                                        "ANALYSIS",
                                        List.of("REQUIREMENTS_ANALYSIS"),
                                        "MEDIUM",
                                        8,
                                        2,
                                        List.of(requirements.get(1).getId())
                                )
                        )
                )
        );

        var tasks = projectWbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId);
        if (tasks.isEmpty()) {
            tasks = projectWbsTaskRepository.findAll();
            for (var task : tasks) {
                if (task.getProject().getId().equals(projectId)) {
                    task.setConfirmed(true);
                }
            }
            projectWbsTaskRepository.saveAll(tasks);
            tasks = projectWbsTaskRepository.findByProjectIdAndConfirmedTrue(projectId);
        }
        return tasks;
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
