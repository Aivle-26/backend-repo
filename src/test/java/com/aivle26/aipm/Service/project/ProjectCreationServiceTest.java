package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.PlanningAgentClient;
import com.aivle26.aipm.client.ai.StoredDocumentFile;
import com.aivle26.aipm.Config.storage.DocumentStorageProperties;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectKeyFeatureRepository;
import com.aivle26.aipm.Repository.project.ProjectPlanningExtractionRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.support.InMemoryS3Mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
@WithMockUser(username = "PM001", roles = "PM")
class ProjectCreationServiceTest {

    @Autowired
    private ProjectCreationService service;

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

    @Autowired
    private DocumentStorageProperties documentStorageProperties;

    @MockitoBean
    private PlanningAgentClient planningAgentClient;

    @MockitoBean
    private S3Client s3Client;

    @Value("${app.document.storage-path}")
    private String storagePath;

    @BeforeEach
    void setUp() throws IOException {
        InMemoryS3Mock.configure(s3Client);
        extractionRepository.deleteAll();
        keyFeatureRepository.deleteAll();
        requiredArtifactRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
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
        assertThat(response.projectName()).isEqualTo("AI Learning Support");
        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(response.llmStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(response.requirementCount()).isEqualTo(2);
        assertThat(response.requiredArtifactCount()).isEqualTo(3);
        assertThat(response.documentCount()).isEqualTo(2);
        assertThat(projectRepository.count()).isEqualTo(1);
        assertThat(projectDocumentRepository.count()).isEqualTo(2);
        assertThat(analysisResultRepository.count()).isEqualTo(1);
        assertThat(projectRequirementRepository.count()).isEqualTo(2);
        assertThat(projectRequirementRepository.findAll())
                .extracting(ProjectRequirement::getPriority)
                .containsExactlyInAnyOrder(RequirementPriority.HIGH, RequirementPriority.UNSPECIFIED);
        assertThat(requiredArtifactRepository.count()).isEqualTo(3);
        assertThat(requiredArtifactRepository
                .existsByProjectIdAndArtifactTypeAndArtifactName(
                        response.projectId(),
                        com.aivle26.aipm.Entity.ProjectArtifactType.ORGANIZATION_CHART,
                        "조직도"
                )).isTrue();
        assertThat(keyFeatureRepository.count()).isEqualTo(2);
        assertThat(extractionRepository.count()).isEqualTo(1);

        ArgumentCaptor<List<StoredDocumentFile>> filesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> enableLlmCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(planningAgentClient).extractDocuments(filesCaptor.capture(), enableLlmCaptor.capture());
        assertThat(filesCaptor.getValue()).hasSize(2);
        assertThat(filesCaptor.getValue())
                .extracting(StoredDocumentFile::originalFileName)
                .containsExactly("rfp.pdf", "memo.txt");
        assertThat(enableLlmCaptor.getValue()).isFalse();
    }

    @Test
    void remapsAgentFileNamesToUploadedNames() {
        List<MultipartFile> files = List.of(pdf("actual-rfp.pdf"), txt("actual-memo.txt"));
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithDocumentNames("project-rfp.pdf", "proposal.txt"));

        service.createDraftFromDocuments(files, true, "PM001");

        List<ProjectRequirement> requirements = projectRequirementRepository.findAll();
        assertThat(requirements)
                .extracting(ProjectRequirement::getSourceDocumentName)
                .containsExactlyInAnyOrder("actual-rfp.pdf", "actual-memo.txt");
        assertThat(projectDocumentRepository.findAll())
                .extracting(document -> document.getOriginalFileName())
                .containsExactlyInAnyOrder("actual-rfp.pdf", "actual-memo.txt");
    }

    @Test
    void failWhenNoFiles() {
        assertApiException(() -> service.createDraftFromDocuments(List.of(), true, "PM001"), "PROJECT_DOCUMENT_REQUIRED");
    }

    @Test
    void failWhenMoreThanTenFiles() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(txt("doc-" + i + ".txt"));
        }
        assertApiException(() -> service.createDraftFromDocuments(files, true, "PM001"), "TOO_MANY_PROJECT_DOCUMENTS");
    }

    @Test
    void failWhenFileTooLarge() {
        byte[] content = new byte[(20 * 1024 * 1024) + 1];
        MultipartFile file = new MockMultipartFile("files", "large.txt", "text/plain", content);
        assertApiException(() -> service.createDraftFromDocuments(List.of(file), true, "PM001"), "PROJECT_DOCUMENT_TOO_LARGE");
    }

    @Test
    void failWhenUnsupportedExtension() {
        MultipartFile file = new MockMultipartFile("files", "bad.exe", "application/octet-stream", "x".getBytes());
        assertApiException(() -> service.createDraftFromDocuments(List.of(file), true, "PM001"), "UNSUPPORTED_PROJECT_DOCUMENT");
    }

    @Test
    void failWhenAgentUnavailable() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLANNING_AGENT_UNAVAILABLE", "unavailable"));
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf")), true, "PM001"), "PLANNING_AGENT_UNAVAILABLE");
    }

    @Test
    void failWhenProjectNameMissing() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithProjectName(null));
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
    }

    @Test
    void failWhenRequirementCategoryInvalid() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithRequirementCategory("UNKNOWN"));
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
    }

    @Test
    void failWhenArtifactTypeInvalid() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithArtifactType("UNKNOWN"));
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
    }

    @Test
    void failWhenRequirementSourceDocumentCannotBeMapped() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithUnknownRequirementSource());
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
    }

    @Test
    void failWhenRequirementIdDuplicated() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithDuplicateRequirementId());
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
    }

    @Test
    void failWhenEndDateBeforeStartDate() {
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(responseWithDates(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 8, 1)));
        assertApiException(() -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"), "INVALID_PLANNING_AGENT_RESPONSE");
    }

    @Test
    void rollbackWhenStorageFailsDuringSave() throws IOException {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("S3 unavailable"));
        when(planningAgentClient.extractDocuments(any(), anyBoolean())).thenReturn(successResponse());
        assertApiException(
                () -> service.createDraftFromDocuments(List.of(pdf("rfp.pdf"), txt("memo.txt")), true, "PM001"),
                "DOCUMENT_STORAGE_ERROR"
        );
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
                        "AI Learning Support",
                        "Build a learning support system.",
                        "OO University",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31),
                        List.of("Dashboard", "AI Analysis"),
                        List.of(
                                new PlanningDocumentExtractResponse.RequiredArtifact("REQUIREMENTS_DEFINITION", "Requirements Spec", "1.0"),
                                new PlanningDocumentExtractResponse.RequiredArtifact("WBS", "WBS", "1.0")
                        ),
                        List.of("Submit deliverables"),
                        List.of("Budget approved"),
                        List.of("Encrypt personal data")
                ),
                List.of(
                        new PlanningDocumentExtractResponse.RequirementCandidate(
                                1L,
                                "Dashboard",
                                "Provide a dashboard.",
                                "FUNCTIONAL",
                                "HIGH",
                                "Dashboard loads successfully.",
                                null,
                                "Dashboard Feature",
                                null,
                                "rfp.pdf",
                                "Provide a dashboard."
                        ),
                        new PlanningDocumentExtractResponse.RequirementCandidate(
                                2L,
                                "Security Log",
                                "Store security logs.",
                                "SECURITY",
                                "UNSPECIFIED",
                                "Security logs are stored.",
                                LocalDate.of(2026, 10, 1),
                                "Security Feature",
                                "Encrypt personal data",
                                "memo.txt",
                                "Store security logs."
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

    private PlanningDocumentExtractResponse responseWithDocumentNames(String firstFileName, String secondFileName) {
        PlanningDocumentExtractResponse response = successResponse();
        List<PlanningDocumentExtractResponse.DocumentResult> documents = List.of(
                new PlanningDocumentExtractResponse.DocumentResult(firstFileName, "PDF", 1200L, "TEXT"),
                new PlanningDocumentExtractResponse.DocumentResult(secondFileName, "TXT", 300L, "TEXT")
        );
        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = List.of(
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        1L,
                        "Dashboard",
                        "Provide a dashboard.",
                        "FUNCTIONAL",
                        "HIGH",
                        "Dashboard loads successfully.",
                        null,
                        "Dashboard Feature",
                        null,
                        firstFileName,
                        "Provide a dashboard."
                ),
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        2L,
                        "Security Log",
                        "Store security logs.",
                        "SECURITY",
                        "MEDIUM",
                        "Security logs are stored.",
                        LocalDate.of(2026, 10, 1),
                        "Security Feature",
                        "Encrypt personal data",
                        secondFileName,
                        "Store security logs."
                )
        );
        return new PlanningDocumentExtractResponse(response.projectInfo(), requirements, documents, response.llmStatus());
    }

    private PlanningDocumentExtractResponse responseWithUnknownRequirementSource() {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.RequirementCandidate first = response.requirementCandidates().getFirst();
        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = List.of(
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        first.requirementId(),
                        first.functionName(),
                        first.requirementText(),
                        first.category(),
                        first.priority(),
                        first.acceptanceCriteria(),
                        first.dueDate(),
                        first.deliverableName(),
                        first.securityCondition(),
                        "unknown.pdf",
                        first.sourceExcerpt()
                )
        );
        return new PlanningDocumentExtractResponse(response.projectInfo(), requirements, response.documents(), response.llmStatus());
    }

    private PlanningDocumentExtractResponse responseWithDuplicateRequirementId() {
        PlanningDocumentExtractResponse response = successResponse();
        PlanningDocumentExtractResponse.RequirementCandidate second = response.requirementCandidates().get(1);
        List<PlanningDocumentExtractResponse.RequirementCandidate> requirements = List.of(
                response.requirementCandidates().getFirst(),
                new PlanningDocumentExtractResponse.RequirementCandidate(
                        1L,
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
