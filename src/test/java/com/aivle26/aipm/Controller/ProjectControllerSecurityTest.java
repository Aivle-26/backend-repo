package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Config.AuthProperties;
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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private AuthProperties authProperties;

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
    void listProjectsAllowsStaff() throws Exception {
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        AuthSessionResponse session = authService.issueSession(staff);

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void artifactStatusAllowsProjectOwnerPm() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/status", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$.totalRequiredCount").value(0))
                .andExpect(jsonPath("$.registrationRate").value(0.0))
                .andExpect(jsonPath("$.approvalCompletionRate").value(0.0));
    }

    @Test
    void artifactStatusRejectsStaff() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(staff);

        mockMvc.perform(get("/api/projects/{projectId}/artifacts/status", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void createProjectDraftAllowsPm() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(post("/api/projects/drafts")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Authorized PM Project",
                                  "description": "draft description",
                                  "pmEmployeeNumber": "PM001",
                                  "plannedStartDate": "2026-07-20",
                                  "plannedEndDate": "2026-08-20"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pmEmployeeNumber").value("PM001"));
    }

    @Test
    void createProjectDraftRejectsStaff() throws Exception {
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        AuthSessionResponse session = authService.issueSession(staff);

        mockMvc.perform(post("/api/projects/drafts")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Forbidden Staff Project",
                                  "description": "draft description",
                                  "pmEmployeeNumber": "STAFF001",
                                  "plannedStartDate": "2026-07-20",
                                  "plannedEndDate": "2026-08-20"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void differentPmCannotMutateProject() throws Exception {
        User owner = userRepository.save(createPmUser("PM001"));
        User otherPm = userRepository.save(createPmUser("PM002"));
        Project project = projectRepository.saveAndFlush(createProject(owner));
        AuthSessionResponse session = authService.issueSession(otherPm);

        mockMvc.perform(post("/api/projects/{projectId}/documents/analyze", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void meAllowsPmAndStaff() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        AuthSessionResponse pmSession = authService.issueSession(pm);
        AuthSessionResponse staffSession = authService.issueSession(staff);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + pmSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PM"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + staffSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthCodes.AUTH_UNAUTHORIZED));
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        String expiredToken = createExpiredToken("PM001", "PM");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthCodes.AUTH_TOKEN_EXPIRED));
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
        return createUser(employeeNumber, "PM");
    }

    private User createUser(String employeeNumber, String role) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(role.equals("PM") ? "Project Manager" : "Staff Member");
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setPassword("encoded-password");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private String createExpiredToken(String employeeNumber, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(employeeNumber)
                .claim("role", role)
                .claim("absoluteExp", now.plusSeconds(3600).toEpochMilli())
                .issuedAt(Date.from(now.minusSeconds(600)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
