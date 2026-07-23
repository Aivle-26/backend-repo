package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.AuthSessionResponse;
import com.aivle26.aipm.Entity.PlanningLlmStatus;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.ProjectKeyFeature;
import com.aivle26.aipm.Entity.ProjectPlanningExtraction;
import com.aivle26.aipm.Entity.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.ProjectRequirement;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.RequirementPriority;
import com.aivle26.aipm.Entity.RequirementStatus;
import com.aivle26.aipm.Entity.RequirementType;
import com.aivle26.aipm.Entity.RequiredArtifactType;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.UserRepository;
import com.aivle26.aipm.Service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectDocumentAnalysisResultsControllerTest {
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
    private ProjectRequiredArtifactRepository requiredArtifactRepository;

    @Autowired
    private ProjectKeyFeatureRepository keyFeatureRepository;

    @Autowired
    private ProjectPlanningExtractionRepository planningExtractionRepository;

    @Autowired
    private ProjectWbsTaskRepository projectWbsTaskRepository;

    @Autowired
    private ProjectWbsResultRepository projectWbsResultRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

    @Autowired
    private ProjectScheduleResultRepository projectScheduleResultRepository;

    private String accessToken;
    private Long projectId;

    @BeforeEach
    void setUp() {
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        planningExtractionRepository.deleteAll();
        keyFeatureRepository.deleteAll();
        requiredArtifactRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User pm = userRepository.save(createPmUser("PM010"));
        projectId = projectRepository.save(createProject(pm)).getId();
        accessToken = authService.issueSession(pm).accessToken();
    }

    @Test
    void returnsStoredProjectDocumentsAndLatestAnalysisResult() throws Exception {
        Project project = projectRepository.findById(projectId).orElseThrow();
        ProjectDocument firstDocument = projectDocumentRepository.save(createDocument(project, "rfp.pdf", 120L));
        projectDocumentRepository.save(createDocument(project, "proposal.docx", 240L));
        ProjectDocumentAnalysisResult olderResult = analysisResultRepository.save(createAnalysisResult(project, "analysis-001", "old-goal"));
        ProjectDocumentAnalysisResult latestResult = analysisResultRepository.save(createAnalysisResult(project, "analysis-002", "latest-goal"));
        projectRequirementRepository.save(createRequirement(project, firstDocument, olderResult, "REQ-OLD"));
        projectRequirementRepository.save(createRequirement(project, firstDocument, latestResult, "REQ-NEW"));
        requiredArtifactRepository.save(createArtifact(project));
        keyFeatureRepository.save(createKeyFeature(project));
        planningExtractionRepository.save(createPlanningExtraction(project));

        mockMvc.perform(get("/api/projects/{projectId}/documents/analysis-results", projectId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectId").value(projectId))
                .andExpect(jsonPath("$.project.pmEmployeeNumber").value("PM010"))
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].originalFileName").value("rfp.pdf"))
                .andExpect(jsonPath("$.analysisResult.agentExecutionId").value("analysis-002"))
                .andExpect(jsonPath("$.analysisResult.projectGoal").value("latest-goal"))
                .andExpect(jsonPath("$.requirements.length()").value(1))
                .andExpect(jsonPath("$.requirements[0].externalReferenceId").value("REQ-NEW"))
                .andExpect(jsonPath("$.requiredArtifacts.length()").value(1))
                .andExpect(jsonPath("$.requiredArtifacts[0].artifactType").value(RequiredArtifactType.WBS.name()))
                .andExpect(jsonPath("$.keyFeatures.length()").value(1))
                .andExpect(jsonPath("$.planningExtraction.llmStatus").value(PlanningLlmStatus.SUCCEEDED.name()));
    }

    @Test
    void returnsNotFoundWhenAnalysisResultMissing() throws Exception {
        Project project = projectRepository.findById(projectId).orElseThrow();
        projectDocumentRepository.save(createDocument(project, "rfp.pdf", 120L));

        mockMvc.perform(get("/api/projects/{projectId}/documents/analysis-results", projectId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_ANALYSIS_RESULT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "프로젝트에 저장된 AI 분석 결과가 없습니다. 문서 분석을 먼저 실행해 주세요. projectId=" + projectId
                ));
    }

    @Test
    void returnsHelpfulMessageWhenProjectMissing() throws Exception {
        Long missingProjectId = projectId + 9999;

        mockMvc.perform(get("/api/projects/{projectId}/documents/analysis-results", missingProjectId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "요청한 프로젝트를 찾을 수 없습니다. projectId=" + missingProjectId
                ));
    }

    @Test
    void returnsHelpfulMessageWhenDocumentMissing() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/documents/analysis-results", projectId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "프로젝트에 업로드된 문서가 없습니다. 문서를 먼저 업로드해 주세요. projectId=" + projectId
                ));
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

    private Project createProject(User pm) {
        Project project = new Project();
        project.setName("Stored Analysis Project");
        project.setDescription("project-description");
        project.setClientOrganization("Client Org");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 7, 23));
        project.setPlannedEndDate(LocalDate.of(2026, 8, 23));
        project.setAcceptanceConditionsJson("[\"accepted\"]");
        project.setBudgetContractConditionsJson("[\"budget\"]");
        project.setSecurityPrivacyConditionsJson("[\"security\"]");
        return project;
    }

    private ProjectDocument createDocument(Project project, String originalFileName, long characterCount) {
        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.ANALYZED);
        document.setOriginalFileName(originalFileName);
        document.setStoredFileName("stored-" + originalFileName);
        document.setStoragePath("uploads/documents/" + originalFileName);
        document.setExtension(originalFileName.substring(originalFileName.lastIndexOf('.') + 1));
        document.setContentType("application/octet-stream");
        document.setFileSize(1024L);
        document.setCharacterCount(characterCount);
        document.setFileType("RFP");
        document.setProcessingMode("OCR");
        return document;
    }

    private ProjectDocumentAnalysisResult createAnalysisResult(Project project, String agentExecutionId, String projectGoal) {
        ProjectDocumentAnalysisResult result = new ProjectDocumentAnalysisResult();
        result.setProject(project);
        result.setAgentExecutionId(agentExecutionId);
        result.setAgentVersion("agent-v1");
        result.setProjectGoal(projectGoal);
        result.setScope("scope");
        result.setDeliverablesJson("[\"deliverable\"]");
        result.setMilestonesJson("[\"milestone\"]");
        result.setTechnologyStacksJson("[\"java\"]");
        result.setConstraintsJson("[\"constraint\"]");
        result.setRisksJson("[\"risk\"]");
        return result;
    }

    private ProjectRequirement createRequirement(
            Project project,
            ProjectDocument document,
            ProjectDocumentAnalysisResult analysisResult,
            String externalReferenceId
    ) {
        ProjectRequirement requirement = new ProjectRequirement();
        requirement.setProject(project);
        requirement.setSourceDocument(document);
        requirement.setAnalysisResult(analysisResult);
        requirement.setExternalReferenceId(externalReferenceId);
        requirement.setType(RequirementType.FUNCTIONAL);
        requirement.setTitle("Requirement " + externalReferenceId);
        requirement.setDescription("Description " + externalReferenceId);
        requirement.setAcceptanceCriteria("criteria");
        requirement.setDueDate(LocalDate.of(2026, 8, 1));
        requirement.setDeliverableName("deliverable");
        requirement.setSecurityCondition("security");
        requirement.setSourceDocumentName(document.getOriginalFileName());
        requirement.setSourceExcerpt("excerpt");
        requirement.setPriority(RequirementPriority.HIGH);
        requirement.setStatus(RequirementStatus.UNCONFIRMED);
        requirement.setConfirmed(false);
        return requirement;
    }

    private ProjectRequiredArtifact createArtifact(Project project) {
        ProjectRequiredArtifact artifact = new ProjectRequiredArtifact();
        artifact.setProject(project);
        artifact.setArtifactType(RequiredArtifactType.WBS);
        artifact.setArtifactName("WBS");
        artifact.setRequiredVersion("v1");
        return artifact;
    }

    private ProjectKeyFeature createKeyFeature(Project project) {
        ProjectKeyFeature keyFeature = new ProjectKeyFeature();
        keyFeature.setProject(project);
        keyFeature.setFeatureName("Feature A");
        return keyFeature;
    }

    private ProjectPlanningExtraction createPlanningExtraction(Project project) {
        ProjectPlanningExtraction extraction = new ProjectPlanningExtraction();
        extraction.setProject(project);
        extraction.setLlmStatus(PlanningLlmStatus.SUCCEEDED);
        extraction.setDocumentCount(2);
        extraction.setRequirementCount(1);
        extraction.setRequiredArtifactCount(1);
        return extraction;
    }
}
