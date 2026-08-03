package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.risk.RiskTeamMember;
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
import com.aivle26.aipm.Repository.risk.RiskTeamMemberRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthCodes;
import com.aivle26.aipm.Service.auth.AuthService;
import com.aivle26.aipm.Config.auth.AuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerSecurityTest {

    @MockitoBean
    private S3Client s3Client;

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

    @Autowired
    private RiskTeamMemberRepository riskTeamMemberRepository;

    @BeforeEach
    void setUp() {
        riskTeamMemberRepository.deleteAll();
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
        mockMvc.perform(get("/api/projects/1/documents"))
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
    void deleteProjectDocumentRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/projects/1/documents/1"))
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
        User otherPm = userRepository.save(createPmUser("PM999"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        projectRepository.saveAndFlush(createProject(otherPm));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$[0].name").value("New PM Project"))
                .andExpect(jsonPath("$[0].pmEmployeeNumber").value("PM001"))
                .andExpect(jsonPath("$[0].status").value(ProjectStatus.DRAFT.name()));
    }

    @Test
    void listProjectDocumentsReturnsStoredMetadataWithBearerToken() throws Exception {
        User pm = userRepository.save(createPmUser("PM002"));
        User otherPm = userRepository.save(createPmUser("PM999"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        ProjectDocument document = projectDocumentRepository.saveAndFlush(createDocument(project));
        Project hiddenProject = projectRepository.saveAndFlush(createProject(otherPm));
        projectDocumentRepository.saveAndFlush(createDocument(hiddenProject));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects/{projectId}/documents", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].documentId").value(document.getId().intValue()))
                .andExpect(jsonPath("$.documents[0].originalFileName").value("저장된-공고문.pdf"))
                .andExpect(jsonPath("$.documents[0].fileSize").value(2048));
    }

    @Test
    void listProjectDocumentsReturnsEmptyListWhenNoDocumentsExist() throws Exception {
        User pm = userRepository.save(createPmUser("PM003"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(get("/api/projects/{projectId}/documents", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$.documents.length()").value(0));
    }

    @Test
    void deleteProjectDocumentAllowsProjectOwnerPm() throws Exception {
        User pm = userRepository.save(createPmUser("PM004"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        ProjectDocument document =
                projectDocumentRepository.saveAndFlush(createDocument(project));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(delete(
                        "/api/projects/{projectId}/documents/{documentId}",
                        project.getId(),
                        document.getId()
                ).header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}/documents", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(0));
    }

    @Test
    void listProjectDocumentsRejectsDifferentProjectOwner() throws Exception {
        User owner = userRepository.save(createPmUser("PM001"));
        User otherPm = userRepository.save(createPmUser("PM002"));
        Project project = projectRepository.saveAndFlush(createProject(owner));
        projectDocumentRepository.saveAndFlush(createDocument(project));
        AuthSessionResponse session = authService.issueSession(otherPm);

        mockMvc.perform(get("/api/projects/{projectId}/documents", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void listProjectDocumentsAllowsParticipatingStaff() throws Exception {
        User owner = userRepository.save(createPmUser("PM001"));
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        Project project = projectRepository.saveAndFlush(createProject(owner));
        ProjectDocument document =
                projectDocumentRepository.saveAndFlush(createDocument(project));
        riskTeamMemberRepository.save(createMembership(
                project.getId(),
                staff.getEmployeeNumber()
        ));
        AuthSessionResponse session = authService.issueSession(staff);

        mockMvc.perform(get("/api/projects/{projectId}/documents", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].documentId")
                        .value(document.getId().intValue()));
    }

    @Test
    void listProjectsAllowsStaff() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        riskTeamMemberRepository.save(createMembership(project.getId(), staff.getEmployeeNumber()));
        AuthSessionResponse session = authService.issueSession(staff);

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(project.getId().intValue()));
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
    void uploadDocumentsAllowsProjectOwnerPm() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "requirements.txt",
                "text/plain",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/{projectId}/documents/upload", project.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$.documents[0].originalFileName").value("requirements.txt"))
                .andExpect(jsonPath("$.documents[0].status").value("UPLOADED"));
    }

    @Test
    void uploadDocumentsRejectsStaff() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(staff);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "requirements.txt",
                "text/plain",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/{projectId}/documents/upload", project.getId())
                        .file(file)
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

        mockMvc.perform(post("/api/projects/{projectId}/requirements/analyze", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[1]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void staffCannotAnalyzeProjectRequirements() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        User staff = userRepository.save(createUser("STAFF001", "STAFF"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(staff);

        mockMvc.perform(post("/api/projects/{projectId}/requirements/analyze", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[1]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void analyzeProjectRequirementsRejectsEmptyDocumentIds() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(post("/api/projects/{projectId}/requirements/analyze", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeProjectRequirementsReturnsNotFoundForMissingProject() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        AuthSessionResponse session = authService.issueSession(pm);

        mockMvc.perform(post("/api/projects/{projectId}/requirements/analyze", 999999)
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[1]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void removedDocumentAnalyzePlaceholderIsNotAvailable() throws Exception {
        User pm = userRepository.save(createPmUser("PM001"));
        Project project = projectRepository.saveAndFlush(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);

        // 문서 개별 삭제 기능(DeleteMapping "/{projectId}/documents/{documentId}") 추가 이후
        // 이 경로는 DELETE 매핑 패턴에 매칭되므로, 제거된 analyze 플레이스홀더로 POST하면
        // 404가 아니라 405(Method Not Allowed)가 반환된다. 어느 쪽이든 analyze 동작은 없다.
        mockMvc.perform(post("/api/projects/{projectId}/documents/analyze", project.getId())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isMethodNotAllowed());
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

    private RiskTeamMember createMembership(Long projectId, String employeeNumber) {
        RiskTeamMember membership = new RiskTeamMember();
        membership.setProjectId(projectId);
        membership.setMemberName(employeeNumber);
        membership.setRole("STAFF");
        membership.setSkills("");
        membership.setWorkloadRate(0);
        membership.setOverdueTaskCount(0);
        membership.setCurrentAssignee(true);
        return membership;
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
