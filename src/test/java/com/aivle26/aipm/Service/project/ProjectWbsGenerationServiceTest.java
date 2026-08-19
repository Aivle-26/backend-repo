package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.PlanningWbsGenerationResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.project.WbsGenerationStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsGenerationRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.client.ai.PlanningWbsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithMockUser(username = "PM001", roles = "PM")
class ProjectWbsGenerationServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private PlanningWbsClient planningWbsClient;

    @Autowired
    private ProjectWbsGenerationService generationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    private ProjectRequirementRepository projectRequirementRepository;

    @Autowired
    private ProjectWbsGenerationRepository generationRepository;

    @Autowired
    private ProjectWbsResultRepository wbsResultRepository;

    @Autowired
    private ProjectWbsTaskRepository wbsTaskRepository;

    @BeforeEach
    void setUp() {
        clearDatabase();
        reset(planningWbsClient);
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    private void clearDatabase() {
        generationRepository.deleteAll();
        wbsTaskRepository.deleteAll();
        wbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void completesGenerationInBackgroundAndPersistsResult() {
        ProjectRequirement requirement = createConfirmedRequirement();
        when(planningWbsClient.generateWbs(any())).thenReturn(response(requirement.getId()));

        var started = generationService.startGeneration(requirement.getProject().getId());

        assertThat(started.status()).isEqualTo(WbsGenerationStatus.PROCESSING);
        assertThat(started.reused()).isFalse();

        var completed = awaitTerminalGeneration(started.generationId());
        assertThat(completed.getStatus()).isEqualTo(WbsGenerationStatus.SUCCEEDED);
        assertThat(completed.getWbsResultId()).isNotNull();
        assertThat(wbsResultRepository.count()).isEqualTo(1);
        assertThat(wbsTaskRepository.count()).isEqualTo(3);
        assertThat(generationService.getLatestGeneration(requirement.getProject().getId()))
                .satisfies(latest -> {
                    assertThat(latest.generationId()).isEqualTo(started.generationId());
                    assertThat(latest.status()).isEqualTo(WbsGenerationStatus.SUCCEEDED);
                    assertThat(latest.wbsResultId()).isEqualTo(completed.getWbsResultId());
                });
    }

    @Test
    void reusesProcessingGenerationForSameProject() throws Exception {
        ProjectRequirement requirement = createConfirmedRequirement();
        CountDownLatch aiStarted = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        when(planningWbsClient.generateWbs(any())).thenAnswer(invocation -> {
            aiStarted.countDown();
            if (!releaseAi.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test AI release timed out");
            }
            return response(requirement.getId());
        });

        var first = generationService.startGeneration(requirement.getProject().getId());
        assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var second = generationService.startGeneration(requirement.getProject().getId());
        assertThat(second.generationId()).isEqualTo(first.generationId());
        assertThat(second.reused()).isTrue();
        assertThat(generationRepository.count()).isEqualTo(1);

        releaseAi.countDown();
        assertThat(awaitTerminalGeneration(first.generationId()).getStatus())
                .isEqualTo(WbsGenerationStatus.SUCCEEDED);
        verify(planningWbsClient, times(1)).generateWbs(any());
    }

    @Test
    void recordsAiFailureForPollingClient() {
        ProjectRequirement requirement = createConfirmedRequirement();
        when(planningWbsClient.generateWbs(any())).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY,
                "PLANNING_WBS_SERVER_ERROR",
                "AI Server에서 WBS를 생성하지 못했습니다."
        ));

        var started = generationService.startGeneration(requirement.getProject().getId());
        var failed = awaitTerminalGeneration(started.generationId());

        assertThat(failed.getStatus()).isEqualTo(WbsGenerationStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("PLANNING_WBS_SERVER_ERROR");
        assertThat(failed.getErrorMessage()).isEqualTo("AI Server에서 WBS를 생성하지 못했습니다.");
        assertThat(wbsResultRepository.count()).isZero();
    }

    private com.aivle26.aipm.Entity.project.ProjectWbsGeneration awaitTerminalGeneration(
            String generationId
    ) {
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(8);
        while (LocalDateTime.now().isBefore(deadline)) {
            var generation = generationRepository.findById(generationId).orElseThrow();
            if (generation.getStatus() != WbsGenerationStatus.PROCESSING) {
                return generation;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for WBS generation", exception);
            }
        }
        throw new AssertionError("WBS generation did not finish in time: " + generationId);
    }

    private ProjectRequirement createConfirmedRequirement() {
        User pm = new User();
        pm.setEmployeeNumber("PM001");
        pm.setName("Project Manager");
        pm.setEmail("pm001@example.com");
        pm.setPassword("encoded-password");
        pm.setRole("PM");
        pm.setStatus(UserStatus.ACTIVE);
        pm.setEmailVerified(true);
        userRepository.saveAndFlush(pm);

        Project project = new Project();
        project.setName("Async WBS Project");
        project.setDescription("WBS timeout test project");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 8, 1));
        project.setPlannedEndDate(LocalDate.of(2026, 8, 31));
        projectRepository.saveAndFlush(project);

        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.ANALYZED);
        document.setOriginalFileName("requirements.txt");
        document.setStoredFileName("requirements.txt");
        document.setStoragePath("test/requirements.txt");
        document.setExtension("txt");
        document.setContentType("text/plain");
        document.setFileSize(100);
        projectDocumentRepository.saveAndFlush(document);

        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(project);
        requirement.setSourceDocument(document);
        requirement.setExternalReferenceId(1L);
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setTitle("Generate WBS asynchronously");
        requirement.setDescription("The server generates WBS in a background task.");
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.CONFIRMED);
        requirement.setConfirmed(true);
        requirement.setIncludedInFinal(true);
        return projectRequirementRepository.saveAndFlush(requirement);
    }

    private PlanningWbsGenerationResponse response(Long requirementId) {
        return new PlanningWbsGenerationResponse(
                "Async WBS Project",
                List.of("개발"),
                List.of(
                        new PlanningWbsGenerationResponse.WbsItem(
                                1L,
                                "1",
                                null,
                                1,
                                1,
                                "PHASE",
                                "개발",
                                "개발 단계",
                                List.of(requirementId)
                        ),
                        new PlanningWbsGenerationResponse.WbsItem(
                                2L,
                                "1.1",
                                1L,
                                2,
                                1,
                                "WORK_PACKAGE",
                                "WBS 생성",
                                "WBS 생성 작업 묶음",
                                List.of(requirementId)
                        ),
                        new PlanningWbsGenerationResponse.WbsItem(
                                3L,
                                "1.1.1",
                                2L,
                                3,
                                1,
                                "TASK",
                                "비동기 WBS 생성",
                                "백그라운드에서 WBS를 생성한다.",
                                List.of(requirementId)
                        )
                ),
                List.of(),
                "SUCCEEDED"
        );
    }
}
