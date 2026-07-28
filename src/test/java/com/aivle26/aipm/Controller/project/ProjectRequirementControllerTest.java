package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsResult;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Entity.project.WbsDifficulty;
import com.aivle26.aipm.Entity.project.WbsPhase;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectRequirementControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private ProjectWbsTaskRepository projectWbsTaskRepository;

    @Autowired
    private ProjectWbsResultRepository projectWbsResultRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

    @Autowired
    private ProjectScheduleResultRepository projectScheduleResultRepository;

    private String accessToken;
    private Project project;
    private ProjectDocument document;
    private ProjectDocumentAnalysisResult analysisResult;

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

        User pm = userRepository.save(createPmUser("PM-REQ"));
        project = projectRepository.save(createProject(pm, "Requirements Project"));
        document = projectDocumentRepository.save(createDocument(project, "requirements.pdf"));
        analysisResult = analysisResultRepository.save(createAnalysisResult(project, "analysis-requirements"));
        accessToken = authService.issueSession(pm).accessToken();
    }

    @Test
    void supportsCrudAndRequirementStateTransitions() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/projects/{projectId}/requirements", project.getId())
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(document.getId(), analysisResult.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNCONFIRMED"))
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.acceptanceCriteria").value("승인 기준"))
                .andReturn();

        Long requirementId = readRequirementId(createResult);

        mockMvc.perform(get("/api/projects/{projectId}/requirements", project.getId())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions.length()").value(0))
                .andExpect(jsonPath("$.finalRequirements.length()").value(1))
                .andExpect(jsonPath("$.finalRequirements[0].requirementId").value(requirementId));

        mockMvc.perform(get("/api/projects/{projectId}/requirements/{requirementId}", project.getId(), requirementId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalReferenceId").value(1));

        mockMvc.perform(patch("/api/projects/{projectId}/requirements/{requirementId}", project.getId(), requirementId)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정된 요구사항",
                                  "priority": "LOW",
                                  "deliverableName": "수정 산출물"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 요구사항"))
                .andExpect(jsonPath("$.priority").value("LOW"));

        mockMvc.perform(patch("/api/projects/{projectId}/requirements/{requirementId}/confirm", project.getId(), requirementId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmed").value(true));

        mockMvc.perform(patch("/api/projects/{projectId}/requirements/{requirementId}/unconfirm", project.getId(), requirementId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNCONFIRMED"))
                .andExpect(jsonPath("$.confirmed").value(false));

        mockMvc.perform(patch("/api/projects/{projectId}/requirements/{requirementId}/reject", project.getId(), requirementId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.confirmed").value(false));

        mockMvc.perform(delete("/api/projects/{projectId}/requirements/{requirementId}", project.getId(), requirementId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isNoContent());

        assertThat(projectRequirementRepository.existsById(requirementId)).isFalse();
    }

    @Test
    void filtersRequirementsAndConfirmsAll() throws Exception {
        projectRequirementRepository.save(createRequirement(1L, RequirementType.FUNCTIONAL, RequirementPriority.HIGH));
        projectRequirementRepository.save(createRequirement(2L, RequirementType.SECURITY, RequirementPriority.HIGH));
        ProjectRequirement rejected = createRequirement(3L, RequirementType.FUNCTIONAL, RequirementPriority.LOW);
        rejected.setStatus(RequirementStatus.REJECTED);
        projectRequirementRepository.save(rejected);

        mockMvc.perform(get("/api/projects/{projectId}/requirements", project.getId())
                        .header("Authorization", bearerToken())
                        .param("type", "FUNCTIONAL")
                        .param("priority", "HIGH")
                        .param("status", "UNCONFIRMED")
                        .param("confirmed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalRequirements.length()").value(1))
                .andExpect(jsonPath("$.finalRequirements[0].externalReferenceId").value(1));

        mockMvc.perform(patch("/api/projects/{projectId}/requirements/confirm", project.getId())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].confirmed").value(true));

        assertThat(projectRequirementRepository.findByProjectIdOrderByIdAsc(project.getId()))
                .allSatisfy(requirement -> {
                    assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.CONFIRMED);
                    assertThat(requirement.isConfirmed()).isTrue();
                });
    }

    @Test
    void preventsDeletingConfirmedOrWbsLinkedRequirement() throws Exception {
        ProjectRequirement confirmed = projectRequirementRepository.save(
                createRequirement(10L, RequirementType.FUNCTIONAL, RequirementPriority.HIGH)
        );
        confirmed.setStatus(RequirementStatus.CONFIRMED);
        confirmed.setConfirmed(true);
        projectRequirementRepository.saveAndFlush(confirmed);

        mockMvc.perform(delete("/api/projects/{projectId}/requirements/{requirementId}", project.getId(), confirmed.getId())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENT_DELETE_CONFIRMED"));

        ProjectRequirement linked = projectRequirementRepository.save(
                createRequirement(11L, RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM)
        );
        ProjectWbsResult wbsResult = projectWbsResultRepository.save(createWbsResult());
        projectWbsTaskRepository.save(createWbsTask(wbsResult, linked));

        mockMvc.perform(delete("/api/projects/{projectId}/requirements/{requirementId}", project.getId(), linked.getId())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENT_DELETE_WBS_LINKED"));
    }

    @Test
    void validatesProjectOwnershipForRelationsAndRequirement() throws Exception {
        Project otherProject = projectRepository.save(createProject(project.getPm(), "Other Project"));
        ProjectDocument otherDocument = projectDocumentRepository.save(createDocument(otherProject, "other.pdf"));
        ProjectDocumentAnalysisResult otherAnalysis = analysisResultRepository.save(
                createAnalysisResult(otherProject, "analysis-other")
        );

        mockMvc.perform(post("/api/projects/{projectId}/requirements", project.getId())
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(otherDocument.getId(), otherAnalysis.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUIREMENT_ANALYSIS_RESULT"));

        mockMvc.perform(post("/api/projects/{projectId}/requirements", project.getId())
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(otherDocument.getId(), analysisResult.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUIREMENT_SOURCE_DOCUMENT"));

        ProjectRequirement otherRequirement = projectRequirementRepository.save(createRequirement(
                otherProject,
                otherDocument,
                otherAnalysis,
                20L,
                RequirementType.DATA,
                RequirementPriority.UNSPECIFIED
        ));

        mockMvc.perform(get("/api/projects/{projectId}/requirements/{requirementId}", project.getId(), otherRequirement.getId())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_REQUIREMENT_NOT_FOUND"));
    }

    private Map<String, Object> createRequest(Long sourceDocumentId, Long analysisResultId) {
        return Map.ofEntries(
                Map.entry("analysisResultId", analysisResultId),
                Map.entry("sourceDocumentId", sourceDocumentId),
                Map.entry("externalReferenceId", 1L),
                Map.entry("type", "FUNCTIONAL"),
                Map.entry("title", "요구사항 CRUD"),
                Map.entry("description", "DB 기반 요구사항을 관리한다."),
                Map.entry("acceptanceCriteria", "승인 기준"),
                Map.entry("dueDate", "2026-12-31"),
                Map.entry("deliverableName", "요구사항 정의서"),
                Map.entry("securityCondition", "권한 검증"),
                Map.entry("sourceDocumentName", "requirements.pdf"),
                Map.entry("sourceExcerpt", "원문 근거"),
                Map.entry("priority", "HIGH")
        );
    }

    private Long readRequirementId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("requirementId").longValue();
    }

    private String bearerToken() {
        return "Bearer " + accessToken;
    }

    private User createPmUser(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Requirement PM");
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setPassword("encoded-password");
        user.setRole("PM");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private Project createProject(User pm, String name) {
        Project value = new Project();
        value.setName(name);
        value.setPm(pm);
        value.setStatus(ProjectStatus.DRAFT);
        value.setPlannedStartDate(LocalDate.of(2026, 8, 1));
        value.setPlannedEndDate(LocalDate.of(2026, 12, 31));
        return value;
    }

    private ProjectDocument createDocument(Project owner, String fileName) {
        ProjectDocument value = new ProjectDocument();
        value.setProject(owner);
        value.setStatus(ProjectDocumentStatus.ANALYZED);
        value.setOriginalFileName(fileName);
        value.setStoredFileName("stored-" + fileName);
        value.setStoragePath("uploads/documents/" + fileName);
        value.setExtension("pdf");
        value.setContentType("application/pdf");
        value.setFileSize(100L);
        return value;
    }

    private ProjectDocumentAnalysisResult createAnalysisResult(Project owner, String executionId) {
        ProjectDocumentAnalysisResult value = new ProjectDocumentAnalysisResult();
        value.setProject(owner);
        value.setAgentExecutionId(executionId);
        value.setAgentVersion("agent-v1");
        value.setProjectGoal("goal");
        value.setScope("scope");
        value.setDeliverablesJson("[]");
        value.setMilestonesJson("[]");
        value.setTechnologyStacksJson("[]");
        value.setConstraintsJson("[]");
        value.setRisksJson("[]");
        return value;
    }

    private ProjectRequirement createRequirement(
            Long referenceId,
            RequirementType type,
            RequirementPriority priority
    ) {
        return createRequirement(project, document, analysisResult, referenceId, type, priority);
    }

    private ProjectRequirement createRequirement(
            Project owner,
            ProjectDocument sourceDocument,
            ProjectDocumentAnalysisResult sourceAnalysis,
            Long referenceId,
            RequirementType type,
            RequirementPriority priority
    ) {
        ProjectRequirement value = new ProjectRequirement();
        value.setProject(owner);
        value.setSourceDocument(sourceDocument);
        value.setAnalysisResult(sourceAnalysis);
        value.setExternalReferenceId(referenceId);
        value.setType(type);
        value.setTitle("Requirement " + referenceId);
        value.setDescription("Description " + referenceId);
        value.setPriority(priority);
        value.setStatus(RequirementStatus.UNCONFIRMED);
        value.setConfirmed(false);
        return value;
    }

    private ProjectWbsResult createWbsResult() {
        ProjectWbsResult value = new ProjectWbsResult();
        value.setProject(project);
        value.setAgentExecutionId("wbs-requirement-delete");
        value.setAgentVersion("wbs-v1");
        return value;
    }

    private ProjectWbsTask createWbsTask(ProjectWbsResult wbsResult, ProjectRequirement requirement) {
        ProjectWbsTask value = new ProjectWbsTask();
        value.setProject(project);
        value.setWbsResult(wbsResult);
        value.setExternalTaskId("TASK-REQ");
        value.setTaskCode("TASK-REQ");
        value.setTaskName("Requirement task");
        value.setDescription("Linked requirement task");
        value.setPhase(WbsPhase.ANALYSIS);
        value.setDifficulty(WbsDifficulty.LOW);
        value.setEstimatedHours(8);
        value.setOrderIndex(1);
        value.setConfirmed(false);
        value.setRequirements(new LinkedHashSet<>(java.util.List.of(requirement)));
        return value;
    }
}
