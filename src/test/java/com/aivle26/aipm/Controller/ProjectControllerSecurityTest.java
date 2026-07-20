package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.AuthSessionResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.UserRepository;
import com.aivle26.aipm.Service.AuthCodes;
import com.aivle26.aipm.Service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
