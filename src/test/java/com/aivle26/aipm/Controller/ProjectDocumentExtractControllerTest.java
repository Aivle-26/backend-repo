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
import com.aivle26.aipm.Service.AiServerDocumentExtractClient;
import com.aivle26.aipm.Service.AiServerJsonResponse;
import com.aivle26.aipm.Service.AuthService;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    private String accessToken;

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

        User pm = userRepository.save(createPmUser("PM001"));
        projectRepository.save(createProject(pm));
        AuthSessionResponse session = authService.issueSession(pm);
        accessToken = session.accessToken();
    }

    @Test
    void uploadSingleFileRelaysAiServerResponse() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("llm_status", "FALLBACK");
        when(aiServerDocumentExtractClient.extractDocuments(anyList()))
                .thenReturn(new AiServerJsonResponse(HttpStatus.OK, body));

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "project-rfp.pdf",
                "application/pdf",
                "pdf-content".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/1/documents/extract")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llm_status").value("FALLBACK"));

        verify(aiServerDocumentExtractClient)
                .extractDocuments(argThat(matchesFileNames("project-rfp.pdf")));
    }

    @Test
    void uploadMultipleFilesRelaysInOriginalOrder() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("llm_status", "SUCCEEDED");
        when(aiServerDocumentExtractClient.extractDocuments(anyList()))
                .thenReturn(new AiServerJsonResponse(HttpStatus.OK, body));

        MockMultipartFile first = new MockMultipartFile("files", "project-rfp.pdf", "application/pdf", "one".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "project-proposal.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "two".getBytes());

        mockMvc.perform(multipart("/api/projects/1/documents/extract")
                        .file(first)
                        .file(second)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llm_status").value("SUCCEEDED"));

        verify(aiServerDocumentExtractClient)
                .extractDocuments(argThat(matchesFileNames("project-rfp.pdf", "project-proposal.docx")));
    }

    @Test
    void emptyFileFailsBeforeRelay() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/projects/1/documents/extract")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_DOCUMENT_EMPTY"));

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    @Test
    void elevenFilesFailBeforeRelay() throws Exception {
        MockMultipartHttpServletRequestBuilder requestBuilder = multipart("/api/projects/1/documents/extract");
        requestBuilder.header("Authorization", "Bearer " + accessToken);
        for (int i = 0; i < 11; i++) {
            requestBuilder.file(new MockMultipartFile("files", "doc-" + i + ".txt", "text/plain", "x".getBytes()));
        }

        mockMvc.perform(requestBuilder)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PROJECT_DOCUMENTS"));

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    @Test
    void fileLargerThanTwentyMbFailsBeforeRelay() throws Exception {
        byte[] largeContent = new byte[20 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("files", "large.txt", "text/plain", largeContent);

        mockMvc.perform(multipart("/api/projects/1/documents/extract")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PROJECT_DOCUMENT_TOO_LARGE"));

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    @Test
    void unsupportedExtensionFailsBeforeRelay() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "unsupported.exe", "application/octet-stream", "abc".getBytes());

        mockMvc.perform(multipart("/api/projects/1/documents/extract")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PROJECT_DOCUMENT"));

        verifyNoInteractions(aiServerDocumentExtractClient);
    }

    private ArgumentMatcher<List<org.springframework.web.multipart.MultipartFile>> matchesFileNames(String... fileNames) {
        return files -> {
            List<String> names = new ArrayList<>();
            for (var file : files) {
                names.add(file.getOriginalFilename());
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
