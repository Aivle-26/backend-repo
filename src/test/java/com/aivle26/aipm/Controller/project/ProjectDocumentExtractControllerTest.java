package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.client.ai.AiServerDocumentExtractClient;
import com.aivle26.aipm.client.ai.AiServerJsonResponse;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.project.Project;
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
import com.aivle26.aipm.Service.auth.AuthService;
import com.aivle26.aipm.support.InMemoryS3Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectDocumentExtractControllerTest {
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

    @MockBean
    private AiServerDocumentExtractClient aiServerDocumentExtractClient;

    @MockitoBean
    private S3Client s3Client;

    private String accessToken;
    private Long projectId;

    @BeforeEach
    void setUp() {
        InMemoryS3Mock.configure(s3Client);
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User pm = userRepository.save(createPmUser("PM001"));
        projectId = projectRepository.save(createProject(pm)).getId();
        AuthSessionResponse session = authService.issueSession(pm);
        accessToken = session.accessToken();
    }

    @Test
    void extractUsesStoredFilesWithoutCreatingNewDocumentRecords() throws Exception {
        uploadDocuments(
                new MockMultipartFile("files", "project-rfp.pdf", "application/pdf", "one".getBytes()),
                new MockMultipartFile("files", "project-proposal.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "two".getBytes())
        );

        long documentCountBefore = projectDocumentRepository.count();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("llm_status", "SUCCEEDED");
        when(aiServerDocumentExtractClient.extractDocuments(anyList()))
                .thenReturn(new AiServerJsonResponse(HttpStatus.OK, body));

        mockMvc.perform(post("/api/projects/{projectId}/documents/extract", projectId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llm_status").value("SUCCEEDED"));

        assertThat(projectDocumentRepository.count()).isEqualTo(documentCountBefore);
        verify(aiServerDocumentExtractClient)
                .extractDocuments(argThat(matchesFileNames("project-rfp.pdf", "project-proposal.docx")));
    }

    @Test
    void extractFailsWhenStoredDocumentMissing() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/documents/extract", projectId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    @Test
    void uploadRejectsEmptyFileBeforeSaving() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/projects/{projectId}/documents/upload", projectId)
                        .file(emptyFile)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    @Test
    void uploadRejectsTooManyFilesBeforeSaving() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/projects/{projectId}/documents/upload", projectId);
        request.with(csrf());
        request.header("Authorization", "Bearer " + accessToken);
        for (int i = 0; i < 11; i++) {
            request.file(new MockMultipartFile("files", "doc-" + i + ".txt", "text/plain", "x".getBytes()));
        }

        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PROJECT_DOCUMENTS"));

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    private void uploadDocuments(MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/projects/{projectId}/documents/upload", projectId);
        request.with(csrf());
        request.header("Authorization", "Bearer " + accessToken);
        for (MockMultipartFile file : files) {
            request.file(file);
        }

        mockMvc.perform(request)
                .andExpect(status().isCreated());
    }

    private ArgumentMatcher<List<StoredDocumentFile>> matchesFileNames(String... fileNames) {
        return files -> {
            List<String> names = new ArrayList<>();
            for (StoredDocumentFile file : files) {
                names.add(file.originalFileName());
            }
            return names.equals(List.of(fileNames));
        };
    }

    private Project createProject(User pm) {
        Project project = new Project();
        project.setName("Relay Project");
        project.setDescription("relay description");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 7, 22));
        project.setPlannedEndDate(LocalDate.of(2026, 8, 22));
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
