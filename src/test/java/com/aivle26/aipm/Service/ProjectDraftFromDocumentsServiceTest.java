package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.DocumentStorageProperties;
import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ProjectDraftFromDocumentsServiceTest {

    @Autowired
    private ProjectDraftFromDocumentsService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    private ProjectRequirementRepository projectRequirementRepository;

    @Autowired
    private ProjectRequiredArtifactRepository requiredArtifactRepository;

    @Autowired
    private ProjectKeyFeatureRepository keyFeatureRepository;

    @Autowired
    private ProjectPlanningExtractionRepository extractionRepository;

    @Autowired
    private DocumentStorageProperties documentStorageProperties;

    @MockitoBean
    private PlanningAgentClient planningAgentClient;

    @Value("${app.document.storage-path}")
    private String storagePath;

    @BeforeEach
    void setUp() throws IOException {
        extractionRepository.deleteAll();
        keyFeatureRepository.deleteAll();
        requiredArtifactRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        documentStorageProperties.setStoragePath(storagePath);
        deleteStorageDirectory(Path.of(storagePath));
        userRepository.save(createPmUser("PM001"));
    }

    @Test
    void createDraftFromDocumentsSuccessWithMultipleFiles() {
        List<MultipartFile> files = List.of(pdf("rfp.pdf"), txt("memo.txt"));
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(successResponse());

        var response = service.createDraftFromDocuments(files, false, "PM001");

        assertThat(response.projectId()).isNotNull();
        assertThat(response.projectName()).isEqualTo("AI 학생 맞춤형 학습지원시스템 구축");
        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(response.llmStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(response.requirementCount()).isEqualTo(2);
        assertThat(response.requiredArtifactCount()).isEqualTo(2);
        assertThat(response.documentCount()).isEqualTo(2);
        assertThat(projectRepository.count()).isEqualTo(1);
        assertThat(projectDocumentRepository.count()).isEqualTo(2);
        assertThat(projectRequirementRepository.count()).isEqualTo(2);
        assertThat(requiredArtifactRepository.count()).isEqualTo(2);
        assertThat(keyFeatureRepository.count()).isEqualTo(2);
        assertThat(extractionRepository.count()).isEqualTo(1);

        ArgumentCaptor<List<MultipartFile>> filesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> enableLlmCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(planningAgentClient).extractDocuments(filesCaptor.capture(), enableLlmCaptor.capture());
        assertThat(filesCaptor.getValue()).hasSize(2);
        assertThat(enableLlmCaptor.getValue()).isFalse();
    }

    @Test
    void failWhenNoFiles() {
        assertApiException(() -> service.createDraftFromDocuments(List.of(), true, "PM001"), "PROJECT_DOCUMENT_REQUIRED");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenMoreThanTenFiles() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(txt("doc-" + i + ".txt"));
        }

        assertApiException(() -> service.createDraftFromDocuments(files, true, "PM001"), "TOO_MANY_PROJECT_DOCUMENTS");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenFileTooLarge() {
        byte[] content = new byte[(20 * 1024 * 1024) + 1];
        MultipartFile file = new MockMultipartFile("files", "large.txt", "text/plain", content);

        assertApiException(() -> service.createDraftFromDocuments(List.of(file), true, "PM001"), "PROJECT_DOCUMENT_TOO_LARGE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenUnsupportedExtension() {
        MultipartFile file = new MockMultipartFile("files", "bad.exe", "application/octet-stream", "x".getBytes());

        assertApiException(() -> service.createDraftFromDocuments(List.of(file), true, "PM001"), "UNSUPPORTED_PROJECT_DOCUMENT");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenAgentUnavailable() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLANNING_AGENT_UNAVAILABLE", "문서 분석 서버에 연결할 수 없습니다."));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf")), true, "PM001"), "PLANNING_AGENT_UNAVAILABLE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenAgentTimeout() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PLANNING_AGENT_TIMEOUT", "문서 분석 처리 시간이 초과되었습니다."));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf")), true, "PM001"), "PLANNING_AGENT_TIMEOUT");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenProjectNameMissing() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithProjectName(null));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenRequirementCategoryInvalid() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithRequirementCategory("UNKNOWN"));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenArtifactTypeInvalid() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithArtifactType("UNKNOWN"));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenDocumentNameDoesNotMatchUpload() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithDocumentName("other.pdf"));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenRequirementIdDuplicated() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithDuplicateRequirementId());

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void failWhenEndDateBeforeStartDate() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithDates(
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 8, 1)
        ));

        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void rollbackWhenStorageFailsDuringSave() throws IOException {
        Path blockedStoragePath = Files.createTempFile("aipm-storage", ".tmp");
        documentStorageProperties.setStoragePath(blockedStoragePath.toString());
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(successResponse());

        try {
            assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "PROJECT_DOCUMENT_SAVE_FAILED");
            assertThat(projectRepository.count()).isZero();
            assertThat(projectDocumentRepository.count()).isZero();
            assertThat(projectRequirementRepository.count()).isZero();
        } finally {
            documentStorageProperties.setStoragePath(storagePath);
            Files.deleteIfExists(blockedStoragePath);
        }
    }

    private void assertApiException(ThrowingRunnable runnable, String code) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private PlanningDocumentExtractResponse successResponse() {
        return new PlanningDocumentExtractResponse(
                new PlanningDocumentExtractResponse.ProjectInfo(
                        "AI 학생 맞춤형 학습지원시스템 구축",
                        "학생별 학습 데이터를 분석해 맞춤형 학습 지원 체계 구축",
                        "OO대학교",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        List.of("대시보드", "AI 분석"),
                        List.of(
                                new PlanningDocumentExtractResponse.RequiredArtifact("REQUIREMENTS_DEFINITION", "요구사항 정의서", "1.0"),
                                new PlanningDocumentExtractResponse.RequiredArtifact("WBS", "작업분해도", "1.0")
                        ),
                        List.of("산출물 제출 후 검수"),
                        List.of("총 사업비 5억원"),
                        List.of("개인정보 암호화")
                ),
                List.of(
                        new PlanningDocumentExtractResponse.RequirementCandidate(
                                "REQ-001",
                                "학습 현황 대시보드",
                                "학생별 학습 현황 대시보드를 제공해야 한다.",
                                "FUNCTIONAL",
                                "HIGH",
                                "학생별 현황이 조회되어야 한다.",
                                null,
                                "대시보드 기능",
                                null,
                                "rfp.pdf",
                                "학생별 학습 현황 대시보드를 제공해야 한다."
                        ),
                        new PlanningDocumentExtractResponse.RequirementCandidate(
                                "REQ-002",
                                "보안 감사 로그",
                                "개인정보 접근 로그를 기록해야 한다.",
                                "SECURITY",
                                "MEDIUM",
                                "접근 이력이 저장되어야 한다.",
                                LocalDate.of(2026, 10, 1),
                                "보안 기능",
                                "개인정보 암호화",
                                "memo.txt",
                                "개인정보 접근 로그를 기록해야 한다."
                        )
                ),
                List.of(
                        new PlanningDocumentExtractResponse.DocumentResult("rfp.pdf", "PDF", 1200L, "TEXT"),
                        new PlanningDocumentExtractResponse.DocumentResult("memo.txt", "TXT", 300L, "TEXT")
                ),
                "SUCCEEDED"
        );
    }

    private PlanningDocumentExtractResponse responseWithProjectName(String name) {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.ProjectInfo info = response.projectInfo();
        return new PlanningDocumentExtractResponse(
                new PlanningDocumentExtractResponse.ProjectInfo(
                        name,
                        info.projectGoal(),
                        info.clientOrganization(),
                        info.periodStart(),
                        info.periodEnd(),
                        info.keyFeatures(),
                        info.requiredArtifacts(),
                        info.acceptanceConditions(),
                        info.budgetContractConditions(),
                        info.securityPrivacyConditions()
                ),
                response.requirementCandidates(),
                response.documents(),
                response.llmStatus()
        );
    }

    private PlanningDocumentExtractResponse responseWithRequirementCategory(String category) {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.RequirementCandidate first = response.requirementCandidates().getFirst();
        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = List.of(
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        first.requirementId(),
                        first.functionName(),
                        first.requirementText(),
                        category,
                        first.priority(),
                        first.acceptanceCriteria(),
                        first.dueDate(),
                        first.deliverableName(),
                        first.securityCondition(),
                        first.sourceDocument(),
                        first.sourceExcerpt()
                )
        );
        return new PlanningDocumentExtractResponse(response.projectInfo(), requirements, List.of(response.documents().getFirst()), response.llmStatus());
    }

    private PlanningDocumentExtractResponse responseWithArtifactType(String type) {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.RequiredArtifact first = response.projectInfo().requiredArtifacts().getFirst();
        PlanningDocumentExtractResponse.ProjectInfo info = response.projectInfo();
        return new PlanningDocumentExtractResponse(
                new PlanningDocumentExtractResponse.ProjectInfo(
                        info.projectName(),
                        info.projectGoal(),
                        info.clientOrganization(),
                        info.periodStart(),
                        info.periodEnd(),
                        info.keyFeatures(),
                        List.of(new PlanningDocumentExtractResponse.RequiredArtifact(type, first.artifactName(), first.requiredVersion())),
                        info.acceptanceConditions(),
                        info.budgetContractConditions(),
                        info.securityPrivacyConditions()
                ),
                response.requirementCandidates(),
                response.documents(),
                response.llmStatus()
        );
    }

    private PlanningDocumentExtractResponse responseWithDocumentName(String fileName) {
        PlanningDocumentExtractResponse response = successResponse();
        List<PlanningDocumentExtractResponse.DocumentResult> documents = List.of(
                new PlanningDocumentExtractResponse.DocumentResult(fileName, "PDF", 1200L, "TEXT"),
                response.documents().get(1)
        );
        return new PlanningDocumentExtractResponse(response.projectInfo(), response.requirementCandidates(), documents, response.llmStatus());
    }

    private PlanningDocumentExtractResponse responseWithDuplicateRequirementId() {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.RequirementCandidate second = response.requirementCandidates().get(1);
        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = List.of(
                response.requirementCandidates().getFirst(),
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        "REQ-001",
                        second.functionName(),
                        second.requirementText(),
                        second.category(),
                        second.priority(),
                        second.acceptanceCriteria(),
                        second.dueDate(),
                        second.deliverableName(),
                        second.securityCondition(),
                        second.sourceDocument(),
                        second.sourceExcerpt()
                )
        );
        return new PlanningDocumentExtractResponse(response.projectInfo(), requirements, response.documents(), response.llmStatus());
    }

    private PlanningDocumentExtractResponse responseWithDates(LocalDate start, LocalDate end) {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.ProjectInfo info = response.projectInfo();
        return new PlanningDocumentExtractResponse(
                new PlanningDocumentExtractResponse.ProjectInfo(
                        info.projectName(),
                        info.projectGoal(),
                        info.clientOrganization(),
                        start,
                        end,
                        info.keyFeatures(),
                        info.requiredArtifacts(),
                        info.acceptanceConditions(),
                        info.budgetContractConditions(),
                        info.securityPrivacyConditions()
                ),
                response.requirementCandidates(),
                response.documents(),
                response.llmStatus()
        );
    }

    private MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile("files", fileName, "application/pdf", "pdf-content".getBytes());
    }

    private MockMultipartFile txt(String fileName) {
        return new MockMultipartFile("files", fileName, "text/plain", "text-content".getBytes());
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
