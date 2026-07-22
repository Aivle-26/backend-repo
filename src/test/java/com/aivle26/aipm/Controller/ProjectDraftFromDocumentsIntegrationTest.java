package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.AuthSessionResponse;
import com.aivle26.aipm.Entity.ProjectDocument;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.UserRepository;
import com.aivle26.aipm.Service.AuthService;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
                "mock-rfp.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "mock-rfp-content".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile memo = new MockMultipartFile(
                "files",
                "notes.txt",
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
                .andExpect(jsonPath("$.projectName").value("mock-rfp 기반 프로젝트"))
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
                .containsExactlyInAnyOrder("mock-rfp.pdf", "notes.txt");
        assertThat(documents)
                .allSatisfy(document -> assertThat(Files.exists(Path.of(document.getStoragePath()))).isTrue());
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
                requestBody = new String(inputStream.readAllBytes(), StandardCharsets.ISO_8859_1);
            }

            List<String> uploadedFiles = List.of("mock-rfp.pdf", "notes.txt").stream()
                    .filter(requestBody::contains)
                    .toList();

            String response = """
                    {
                      "project_info": {
                        "project_name": "mock-rfp 기반 프로젝트",
                        "project_goal": "업로드된 문서를 기반으로 프로젝트 초안을 생성합니다.",
                        "client_organization": "Mock Client",
                        "period_start": "2026-07-22",
                        "period_end": "2026-09-30",
                        "key_features": ["문서 업로드", "요구사항 추출", "초안 생성"],
                        "required_artifacts": [
                          {"artifact_type": "RFP", "artifact_name": "제안요청서", "required_version": "v1.0"},
                          {"artifact_type": "WBS", "artifact_name": "작업분류체계", "required_version": "v1.0"}
                        ],
                        "acceptance_conditions": ["요구사항이 저장되어야 한다."],
                        "budget_contract_conditions": ["예산 별도 협의"],
                        "security_privacy_conditions": ["개인정보 비식별 처리"]
                      },
                      "requirement_candidates": [
                        {
                          "requirement_id": "REQ-001",
                          "function_name": "문서 업로드 분석",
                          "requirement_text": "업로드된 문서를 분석하여 요구사항을 생성해야 한다.",
                          "category": "FUNCTIONAL",
                          "priority": "HIGH",
                          "acceptance_criteria": "문서 업로드 후 요구사항이 생성된다.",
                          "due_date": "2026-08-15",
                          "deliverable_name": "요구사항 정의서",
                          "security_condition": "문서는 내부 저장소에만 보관",
                          "source_document": "%s",
                          "source_excerpt": "문서 업로드 후 분석 기능"
                        },
                        {
                          "requirement_id": "REQ-002",
                          "function_name": "문서 메타데이터 저장",
                          "requirement_text": "분석된 문서 메타데이터를 저장해야 한다.",
                          "category": "DATA",
                          "priority": "MEDIUM",
                          "acceptance_criteria": "문서 메타데이터가 DB에 저장된다.",
                          "due_date": "2026-08-20",
                          "deliverable_name": "문서 목록",
                          "security_condition": "원본 파일명 추적 가능",
                          "source_document": "%s",
                          "source_excerpt": "원본 파일명을 저장"
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
