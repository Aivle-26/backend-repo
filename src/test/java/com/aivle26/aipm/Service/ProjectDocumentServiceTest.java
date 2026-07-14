package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProjectDocumentServiceTest {

    @Autowired
    private ProjectDocumentService projectDocumentService;

    @Autowired
    private ProjectService projectService;

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

    @Value("${app.document.storage-path}")
    private String storagePath;

    @BeforeEach
    void setUp() throws IOException {
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        deleteStorageDirectory();
        userRepository.save(createPmUser("PM001"));
    }

    @Test
    void uploadInitialDocumentsSuccess() {
        Long projectId = createProjectId();
        MockMultipartFile file = new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes());

        ProjectDocumentUploadResponse response = projectDocumentService.uploadInitialDocuments(projectId, List.of(file));

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.documents()).hasSize(1);
        assertThat(response.documents().getFirst().originalFileName()).isEqualTo("requirements.txt");
        assertThat(projectDocumentRepository.count()).isEqualTo(1);
    }

    @Test
    void uploadInitialDocumentsFailWhenExtensionInvalid() {
        Long projectId = createProjectId();
        MockMultipartFile file = new MockMultipartFile("files", "bad.exe", "application/octet-stream", "abc".getBytes());

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(projectId, List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        assertThat(projectDocumentRepository.count()).isZero();
    }

    @Test
    void uploadInitialDocumentsFailWhenFileEmpty() {
        Long projectId = createProjectId();
        MockMultipartFile file = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(projectId, List.of(file)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        assertThat(projectDocumentRepository.count()).isZero();
    }

    @Test
    void uploadInitialDocumentsRollbackWhenAnyFileInvalid() throws IOException {
        Long projectId = createProjectId();
        MockMultipartFile validFile = new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes());
        MockMultipartFile invalidFile = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> projectDocumentService.uploadInitialDocuments(projectId, List.of(validFile, invalidFile)))
                .isInstanceOf(ApiException.class)
                .hasMessage("invalid file");

        assertThat(projectDocumentRepository.count()).isZero();
        Path directory = Path.of(storagePath);
        if (Files.exists(directory)) {
            try (var files = Files.list(directory)) {
                assertThat(files.count()).isZero();
            }
        }
    }

    private Long createProjectId() {
        return projectService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project",
                "draft description",
                "PM001",
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        )).projectId();
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

    private void deleteStorageDirectory() throws IOException {
        Path directory = Path.of(storagePath);
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
}
