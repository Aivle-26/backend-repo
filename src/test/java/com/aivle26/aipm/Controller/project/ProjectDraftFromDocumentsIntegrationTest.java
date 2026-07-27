package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthService;
import com.aivle26.aipm.support.InMemoryS3Mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectDraftFromDocumentsIntegrationTest {

    private static HttpServer mockAiServer;
    private static int mockAiPort;

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
    private ProjectRequiredArtifactRepository requiredArtifactRepository;

    @Autowired
    private ProjectKeyFeatureRepository keyFeatureRepository;

    @Autowired
    private ProjectPlanningExtractionRepository extractionRepository;

    @MockitoBean
    private S3Client s3Client;

    private InMemoryS3Mock.Store s3Store;

    @Value("${app.document.storage-path}")
    private String storagePath;

    @BeforeAll
    static void startMockAiServer() throws IOException {
        mockAiServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockAiPort = mockAiServer.getAddress().getPort();
        mockAiServer.createContext("/api/v1/planning/documents/extract", new MockPlanningHandler());
        mockAiServer.start();
    }

    @AfterAll
    static void stopMockAiServer() {
        if (mockAiServer != null) {
            mockAiServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("agent.planning.base-url", () -> "http://127.0.0.1:" + mockAiPort);
    }

    @BeforeEach
    void setUp() throws IOException {
        s3Store = InMemoryS3Mock.configure(s3Client);
        extractionRepository.deleteAll();
        keyFeatureRepository.deleteAll();
        requiredArtifactRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        deleteStorageDirectory(Path.of(storagePath));
    }

    @Test
    void uploadDocumentsThenPersistStructuredResultFromMockAiServer() throws Exception {
        User pm = userRepository.save(createPmUser("PM9001"));
        AuthSessionResponse session = authService.issueSession(pm);

        MockMultipartFile rfp = new MockMultipartFile(
                "files",
                "한글-공고문.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "mock-rfp-content".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile memo = new MockMultipartFile(
                "files",
                "요구사항-메모.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "mock-notes-content".getBytes(StandardCharsets.UTF_8)
        );

        String body = mockMvc.perform(multipart("/api/projects/drafts/from-documents")
                        .file(rfp)
                        .file(memo)
                        .param("enableLlm", "true")
                        .with(csrf())
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectName").value("문서 기반 한글 프로젝트"))
                .andExpect(jsonPath("$.status").value(ProjectStatus.DRAFT.name()))
                .andExpect(jsonPath("$.llmStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.requirementCount").value(2))
                .andExpect(jsonPath("$.requiredArtifactCount").value(2))
                .andExpect(jsonPath("$.documentCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode response = objectMapper.readTree(body);
        long projectId = response.get("projectId").asLong();

        assertThat(projectRepository.count()).isEqualTo(1);
        assertThat(analysisResultRepository.count()).isEqualTo(1);
        assertThat(projectRequirementRepository.count()).isEqualTo(2);
        assertThat(requiredArtifactRepository.count()).isEqualTo(2);
        assertThat(keyFeatureRepository.count()).isEqualTo(3);
        assertThat(extractionRepository.count()).isEqualTo(1);

        List<ProjectDocument> documents = projectDocumentRepository.findByProjectId(projectId);
        assertThat(documents).hasSize(2);
        assertThat(documents)
                .extracting(ProjectDocument::getOriginalFileName)
                .containsExactlyInAnyOrder("한글-공고문.pdf", "요구사항-메모.txt");
        assertThat(documents)
                .allSatisfy(document -> assertThat(s3Store.contains(document.getStoragePath())).isTrue());

        assertThat(projectRepository.findById(projectId).orElseThrow())
                .satisfies(project -> {
                    assertThat(project.getName()).isEqualTo("문서 기반 한글 프로젝트");
                    assertThat(project.getDescription()).isEqualTo("한글 설명 저장과 조회를 검증합니다.");
                });

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("문서 기반 한글 프로젝트"))
                .andExpect(jsonPath("$[0].description").value("한글 설명 저장과 조회를 검증합니다."));
    }

    @Test
    void preserveKoreanProjectNameAndDescriptionFromJsonRequestThroughListResponse() throws Exception {
        User pm = userRepository.save(createPmUser("PM9002"));
        AuthSessionResponse session = authService.issueSession(pm);
        byte[] requestBody = """
                {
                  "name": "한글 프로젝트 생성 검증",
                  "description": "한글 설명 저장과 조회 검증",
                  "pmEmployeeNumber": "PM9002",
                  "plannedStartDate": "2026-07-23",
                  "plannedEndDate": "2026-08-23"
                }
                """.getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/projects/drafts")
                        .with(csrf())
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("한글 프로젝트 생성 검증"));

        Project savedProject = projectRepository.findAll().get(0);
        assertThat(savedProject.getName()).isEqualTo("한글 프로젝트 생성 검증");
        assertThat(savedProject.getDescription()).isEqualTo("한글 설명 저장과 조회 검증");

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("한글 프로젝트 생성 검증"))
                .andExpect(jsonPath("$[0].description").value("한글 설명 저장과 조회 검증"));
    }

    private User createPmUser(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Mock PM");
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setPassword("encoded-password");
        user.setRole("PM");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private void deleteStorageDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static final class MockPlanningHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String requestBody;
            try (InputStream inputStream = exchange.getRequestBody()) {
                requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            List<String> uploadedFiles = List.of("한글-공고문.pdf", "요구사항-메모.txt").stream()
                    .filter(requestBody::contains)
                    .toList();

            String response = """
                    {
                      "project_info": {
                        "project_name": "문서 기반 한글 프로젝트",
                        "project_goal": "한글 설명 저장과 조회를 검증합니다.",
                        "client_organization": "Mock Client",
                        "period_start": "2026-07-22",
                        "period_end": "2026-09-30",
                        "key_features": [
                          "\\uBB38\\uC11C \\uC5C5\\uB85C\\uB4DC",
                          "\\uC694\\uAD6C\\uC0AC\\uD56D \\uCD94\\uCD9C",
                          "\\uCD08\\uC548 \\uC0DD\\uC131"
                        ],
                        "required_artifacts": [
                          {
                            "artifact_type": "RFP",
                            "artifact_name": "\\uC81C\\uC548\\uC694\\uCCAD\\uC11C",
                            "required_version": "v1.0"
                          },
                          {
                            "artifact_type": "WBS",
                            "artifact_name": "WBS",
                            "required_version": "v1.0"
                          }
                        ],
                        "acceptance_conditions": [
                          "\\uAE30\\uB2A5 \\uD14C\\uC2A4\\uD2B8 \\uD1B5\\uACFC"
                        ],
                        "budget_contract_conditions": [
                          "\\uC608\\uC0B0 \\uBCC4\\uB3C4 \\uD611\\uC758"
                        ],
                        "security_privacy_conditions": [
                          "\\uAC1C\\uC778\\uC815\\uBCF4 \\uBE44\\uC2DD\\uBCC4 \\uCC98\\uB9AC"
                        ]
                      },
                      "requirement_candidates": [
                        {
                          "requirement_id": 1,
                          "function_name": "\\uBB38\\uC11C \\uC5C5\\uB85C\\uB4DC \\uBD84\\uC11D",
                          "requirement_text": "\\uC5C5\\uB85C\\uB4DC\\uD55C \\uBB38\\uC11C\\uB97C \\uBD84\\uC11D\\uD558\\uC5EC \\uC694\\uAD6C\\uC0AC\\uD56D\\uC744 \\uC0DD\\uC131\\uD574\\uC57C \\uD55C\\uB2E4.",
                          "category": "FUNCTIONAL",
                          "priority": "HIGH",
                          "acceptance_criteria": "\\uBB38\\uC11C \\uC5C5\\uB85C\\uB4DC \\uD6C4 \\uC694\\uAD6C\\uC0AC\\uD56D\\uC774 \\uC0DD\\uC131\\uB41C\\uB2E4.",
                          "due_date": "2026-08-15",
                          "deliverable_name": "\\uC694\\uAD6C\\uC0AC\\uD56D \\uC815\\uC758\\uC11C",
                          "security_condition": "\\uBB38\\uC11C\\uB294 \\uC804\\uC6A9 \\uC800\\uC7A5\\uC18C\\uC5D0\\uB9CC \\uBCF4\\uAD00",
                          "source_document": "%s",
                          "source_excerpt": "\\uBB38\\uC11C \\uC5C5\\uB85C\\uB4DC \\uBC0F \\uBD84\\uC11D \\uAE30\\uB2A5"
                        },
                        {
                          "requirement_id": 2,
                          "function_name": "\\uBB38\\uC11C \\uBA54\\uD0C0\\uB370\\uC774\\uD130 \\uC800\\uC7A5",
                          "requirement_text": "\\uBD84\\uC11D\\uD55C \\uBB38\\uC11C \\uBA54\\uD0C0\\uB370\\uC774\\uD130\\uB97C \\uC800\\uC7A5\\uD574\\uC57C \\uD55C\\uB2E4.",
                          "category": "DATA",
                          "priority": "MEDIUM",
                          "acceptance_criteria": "\\uBB38\\uC11C \\uBA54\\uD0C0\\uB370\\uC774\\uD130\\uAC00 DB\\uC5D0 \\uC800\\uC7A5\\uB41C\\uB2E4.",
                          "due_date": "2026-08-20",
                          "deliverable_name": "\\uBB38\\uC11C \\uBAA9\\uB85D",
                          "security_condition": "\\uC6D0\\uBCF8 \\uD30C\\uC77C\\uBA85 \\uCD94\\uC801 \\uAC00\\uB2A5",
                          "source_document": "%s",
                          "source_excerpt": "\\uC6D0\\uBCF8 \\uD30C\\uC77C\\uBA85\\uC744 \\uC800\\uC7A5"
                        }
                      ],
                      "documents": [
                        {
                          "file_name": "%s",
                          "file_type": "RFP",
                          "character_count": 1200,
                          "processing_mode": "MOCK"
                        },
                        {
                          "file_name": "%s",
                          "file_type": "TXT",
                          "character_count": 320,
                          "processing_mode": "MOCK"
                        }
                      ],
                      "llm_status": "SUCCEEDED"
                    }
                    """.formatted(
                    uploadedFiles.get(0),
                    uploadedFiles.get(1),
                    uploadedFiles.get(0),
                    uploadedFiles.get(1)
            );

            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }
}
