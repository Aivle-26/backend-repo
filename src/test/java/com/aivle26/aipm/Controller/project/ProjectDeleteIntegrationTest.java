package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.project.PlanningLlmStatus;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectKeyFeature;
import com.aivle26.aipm.Entity.project.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectScheduleResult;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsResult;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.project.WbsDifficulty;
import com.aivle26.aipm.Entity.project.WbsPhase;
import com.aivle26.aipm.Entity.project.WbsSkill;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthService;
import com.aivle26.aipm.support.InMemoryS3Mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectDeleteIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

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
    private ProjectRequiredArtifactRepository projectRequiredArtifactRepository;

    @Autowired
    private ProjectKeyFeatureRepository projectKeyFeatureRepository;

    @Autowired
    private ProjectPlanningExtractionRepository projectPlanningExtractionRepository;

    @Autowired
    private ProjectWbsTaskRepository projectWbsTaskRepository;

    @Autowired
    private ProjectWbsResultRepository projectWbsResultRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

    @Autowired
    private ProjectScheduleResultRepository projectScheduleResultRepository;

    @MockitoBean
    private S3Client s3Client;

    private InMemoryS3Mock.Store s3Store;

    private String ownerAccessToken;
    private User owner;
    private User otherPm;

    @BeforeEach
    void setUp() throws Exception {
        s3Store = InMemoryS3Mock.configure(s3Client);
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectPlanningExtractionRepository.deleteAll();
        projectKeyFeatureRepository.deleteAll();
        projectRequiredArtifactRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        Files.createDirectories(Path.of("build/test-uploads/documents"));

        owner = userRepository.save(createPmUser("PM001"));
        otherPm = userRepository.save(createPmUser("PM002"));
        AuthSessionResponse session = authService.issueSession(owner);
        ownerAccessToken = session.accessToken();
    }

    @Test
    void deleteProjectRemovesRelatedRowsAndStoredFiles() throws Exception {
        Project project = projectRepository.save(createProject(owner, "Delete Target"));
        ProjectDocument document = projectDocumentRepository.save(createProjectDocument(project, "delete-me.txt"));
        s3Store.put(document.getStoragePath(), "delete me".getBytes());

        ProjectDocumentAnalysisResult analysisResult = analysisResultRepository.save(createAnalysisResult(project));
        ProjectRequirement requirement = projectRequirementRepository.save(createRequirement(project, document, analysisResult));
        projectRequiredArtifactRepository.save(createRequiredArtifact(project));
        projectKeyFeatureRepository.save(createKeyFeature(project));
        projectPlanningExtractionRepository.save(createPlanningExtraction(project));

        ProjectWbsResult wbsResult = projectWbsResultRepository.save(createWbsResult(project));
        ProjectWbsTask parentTask = projectWbsTaskRepository.save(createWbsTask(project, wbsResult, null, "TASK-1", "Parent Task"));
        ProjectWbsTask childTask = createWbsTask(project, wbsResult, parentTask, "TASK-2", "Child Task");
        childTask.setRequirements(Set.of(requirement));
        projectWbsTaskRepository.save(childTask);

        ProjectScheduleResult scheduleResult = projectScheduleResultRepository.save(createScheduleResult(project));
        ProjectSchedule firstSchedule = projectScheduleRepository.save(createSchedule(project, scheduleResult, parentTask, "SCH-1"));
        ProjectSchedule secondSchedule = createSchedule(project, scheduleResult, childTask, "SCH-2");
        secondSchedule.setPredecessors(Set.of(firstSchedule));
        projectScheduleRepository.save(secondSchedule);

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isNoContent());

        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(projectDocumentRepository.findByProjectId(project.getId())).isEmpty();
        assertThat(projectRequirementRepository.count()).isZero();
        assertThat(projectRequiredArtifactRepository.count()).isZero();
        assertThat(projectKeyFeatureRepository.count()).isZero();
        assertThat(projectPlanningExtractionRepository.count()).isZero();
        assertThat(projectWbsTaskRepository.count()).isZero();
        assertThat(projectWbsResultRepository.count()).isZero();
        assertThat(projectScheduleRepository.count()).isZero();
        assertThat(projectScheduleResultRepository.count()).isZero();
        assertThat(analysisResultRepository.count()).isZero();
        assertThat(s3Store.contains(document.getStoragePath())).isFalse();
    }

    @Test
    void deleteProjectSucceedsWhenStoredFileAlreadyMissing() throws Exception {
        Project project = projectRepository.save(createProject(owner, "Delete Missing File Target"));
        ProjectDocument document = projectDocumentRepository.save(createProjectDocument(project, "already-missing.txt"));

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isNoContent());

        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(projectDocumentRepository.findByProjectId(project.getId())).isEmpty();
        assertThat(s3Store.contains(document.getStoragePath())).isFalse();
    }

    @Test
    void deleteProjectRejectsNonOwner() throws Exception {
        Project project = projectRepository.save(createProject(otherPm, "Other Project"));
        AuthSessionResponse session = authService.issueSession(otherPm);

        mockMvc.perform(delete("/api/projects/{projectId}", project.getId())
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_DELETE_FORBIDDEN"));

        assertThat(projectRepository.findById(project.getId())).isPresent();
        assertThat(session.accessToken()).isNotBlank();
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

    private Project createProject(User pm, String name) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("draft description");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 7, 22));
        project.setPlannedEndDate(LocalDate.of(2026, 9, 30));
        return project;
    }

    private ProjectDocument createProjectDocument(Project project, String fileName) {
        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.UPLOADED);
        document.setOriginalFileName(fileName);
        document.setStoredFileName(fileName);
        document.setStoragePath("build/test-uploads/documents/" + fileName);
        document.setExtension("txt");
        document.setContentType("text/plain");
        document.setFileSize(9L);
        document.setCharacterCount(9L);
        document.setFileType("TXT");
        document.setProcessingMode("TEXT");
        return document;
    }

    private ProjectDocumentAnalysisResult createAnalysisResult(Project project) {
        ProjectDocumentAnalysisResult result = new ProjectDocumentAnalysisResult();
        result.setProject(project);
        result.setAgentExecutionId("analysis-delete-1");
        result.setAgentVersion("1.0.0");
        result.setProjectGoal("Delete test goal");
        result.setScope("Delete test scope");
        result.setDeliverablesJson("[]");
        result.setMilestonesJson("[]");
        result.setTechnologyStacksJson("[]");
        result.setConstraintsJson("[]");
        result.setRisksJson("[]");
        return result;
    }

    private ProjectRequirement createRequirement(Project project, ProjectDocument document, ProjectDocumentAnalysisResult analysisResult) {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(project);
        requirement.setAnalysisResult(analysisResult);
        requirement.setSourceDocument(document);
        requirement.setExternalReferenceId(1L);
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setTitle("Requirement");
        requirement.setDescription("Requirement description");
        requirement.setAcceptanceCriteria("Acceptance criteria");
        requirement.setDeliverableName("Requirements Definition");
        requirement.setSourceDocumentName(document.getOriginalFileName());
        requirement.setSourceExcerpt("excerpt");
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.UNCONFIRMED);
        requirement.setConfirmed(false);
        return requirement;
    }

    private ProjectRequiredArtifact createRequiredArtifact(Project project) {
        ProjectRequiredArtifact artifact = new ProjectRequiredArtifact();
        artifact.setProject(project);
        artifact.setArtifactType(ProjectArtifactType.REQUIREMENTS_DEFINITION);
        artifact.setArtifactName("Requirements Definition");
        artifact.setRequiredVersion("1.0");
        return artifact;
    }

    private ProjectKeyFeature createKeyFeature(Project project) {
        ProjectKeyFeature feature = new ProjectKeyFeature();
        feature.setProject(project);
        feature.setFeatureName("Dashboard");
        return feature;
    }

    private ProjectPlanningExtraction createPlanningExtraction(Project project) {
        ProjectPlanningExtraction extraction = new ProjectPlanningExtraction();
        extraction.setProject(project);
        extraction.setLlmStatus(PlanningLlmStatus.SUCCEEDED);
        extraction.setDocumentCount(1);
        extraction.setRequirementCount(1);
        extraction.setRequiredArtifactCount(1);
        return extraction;
    }

    private ProjectWbsResult createWbsResult(Project project) {
        ProjectWbsResult result = new ProjectWbsResult();
        result.setProject(project);
        result.setAgentExecutionId("wbs-delete-1");
        result.setAgentVersion("1.0.0");
        return result;
    }

    private ProjectWbsTask createWbsTask(Project project, ProjectWbsResult wbsResult, ProjectWbsTask parentTask, String externalTaskId, String taskName) {
        ProjectWbsTask task = new ProjectWbsTask();
        task.setProject(project);
        task.setWbsResult(wbsResult);
        task.setParentTask(parentTask);
        task.setExternalTaskId(externalTaskId);
        task.setParentExternalTaskId(parentTask == null ? null : parentTask.getExternalTaskId());
        task.setTaskCode(externalTaskId);
        task.setTaskName(taskName);
        task.setDescription(taskName + " description");
        task.setPhase(WbsPhase.DEVELOPMENT);
        task.setRequiredSkills(Set.of(WbsSkill.BACKEND_DEVELOPMENT));
        task.setDifficulty(WbsDifficulty.MEDIUM);
        task.setEstimatedHours(8);
        task.setOrderIndex(parentTask == null ? 1 : 2);
        task.setConfirmed(false);
        return task;
    }

    private ProjectScheduleResult createScheduleResult(Project project) {
        ProjectScheduleResult result = new ProjectScheduleResult();
        result.setProject(project);
        result.setAgentExecutionId("schedule-delete-1");
        result.setAgentVersion("1.0.0");
        result.setProjectStartDate(LocalDate.of(2026, 7, 22));
        result.setTargetEndDate(LocalDate.of(2026, 9, 30));
        return result;
    }

    private ProjectSchedule createSchedule(Project project, ProjectScheduleResult scheduleResult, ProjectWbsTask wbsTask, String externalScheduleId) {
        ProjectSchedule schedule = new ProjectSchedule();
        schedule.setProject(project);
        schedule.setScheduleResult(scheduleResult);
        schedule.setWbsTask(wbsTask);
        schedule.setExternalScheduleId(externalScheduleId);
        schedule.setStartDate(LocalDate.of(2026, 7, 22));
        schedule.setEndDate(LocalDate.of(2026, 7, 25));
        schedule.setEstimatedDays(4);
        schedule.setMilestone(false);
        schedule.setBufferDays(0);
        schedule.setConfirmed(false);
        return schedule;
    }
}
