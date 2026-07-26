package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectStatus;
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
import com.aivle26.aipm.Service.auth.AuthCodes;
import com.aivle26.aipm.Service.auth.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerSecurityTest {
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
    private ProjectWbsTaskRepository projectWbsTaskRepository;

    @Autowired
    private ProjectWbsResultRepository projectWbsResultRepository;

    @Autowired
    private ProjectScheduleRepository projectScheduleRepository;

    @Autowired
    private ProjectScheduleResultRepository projectScheduleResultRepository;

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
    }

    @Test
    void listProjectsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthCodes.AUTH_UNAUTHORIZED));
    }

    @Test
    void listProjectDocumentsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthCodes.AUTH_UNAUTHORIZED));
    }

    @Test
    void deleteProjectRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/projects/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthCodes.AUTH_UNAUTHORIZED));
    }

    @Test
    void getDocumentAnalysisResultsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects/1/documents/analysis-results"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthCodes.AUTH_UNAUTHORIZED));
    }

    @Test
    void listProjectsReturnsProjectSummariesWithBearerToken() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$[0].name").value("New PM Project"))
                .andExpect(jsonPath("$[0].pmEmployeeNumber").value("PM001"))
                .andExpect(jsonPath("$[0].status").value(ProjectStatus.DRAFT.name()));
    }

    @Test
    void listProjectDocumentsReturnsStoredMetadataWithBearerToken() throws Exception {
        User pm = userRepository.save(createPmUser("PM002"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        ProjectDocument document = projectDocumentRepository.saveAndFlush(createDocument(project));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects/documents")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$[0].documents.length()").value(1))
                .andExpect(jsonPath("$[0].documents[0].documentId").value(document.getId().intValue()))
                .andExpect(jsonPath("$[0].documents[0].originalFileName").value("저장된-공고문.pdf"))
                .andExpect(jsonPath("$[0].documents[0].fileSize").value(2048));
    }

    @Test
    void listProjectDocumentsReturnsEmptyListWhenNoDocumentsExist() throws Exception {
        User pm = userRepository.save(createPmUser("PM003"));
        projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects/documents")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void corsAllowsVercelFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/projects")
                        .header("Origin", "https://frontend-repo-coral.vercel.app")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend-repo-coral.vercel.app"));
    }

    private Project createProject(User pm) {
        Project project = new Project();
        project.setName("New PM Project");
        project.setDescription("draft description");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 7, 20));
        project.setPlannedEndDate(LocalDate.of(2026, 8, 20));
        return project;
    }

    private ProjectDocument createDocument(Project project) {
        ProjectDocument document = new ProjectDocument();
        document.setProject(project);
        document.setStatus(ProjectDocumentStatus.UPLOADED);
        document.setOriginalFileName("저장된-공고문.pdf");
        document.setStoredFileName("stored-document.pdf");
        document.setStoragePath("uploads/documents/stored-document.pdf");
        document.setExtension("pdf");
        document.setContentType("application/pdf");
        document.setFileSize(2048L);
        return document;
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
