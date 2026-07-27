package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.DocumentAnalysisRequirementRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectDocument;
import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;
import com.aivle26.aipm.Entity.project.ProjectDocumentStatus;
import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@WithMockUser(username = "PM001", roles = "PM")
class ProjectDocumentAnalysisServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @Autowired
    private ProjectDocumentAnalysisService projectDocumentAnalysisService;

    @Autowired
    private ProjectDocumentService projectDocumentService;

    @Autowired
    private ProjectCreationService projectCreationService;

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
    private ObjectMapper objectMapper;

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
        userRepository.save(createPmUser("PM001"));
    }

    @Test
    void saveAnalysisResultSuccess() {
        ProjectDocument document = uploadDocument();

        SaveDocumentAnalysisResultResponse response = projectDocumentAnalysisService.saveAnalysisResult(
                document.getProject().getId(),
                new SaveDocumentAnalysisResultRequest(
                        "analysis-20260713-001",
                        "document-agent-v1",
                        "AI 기반 프로젝트 관리 플랫폼 구축",
                        "프로젝트 생성 및 관리 자동화",
                        List.of(new DocumentAnalysisRequirementRequest(
                                1L,
                                "FUNCTIONAL",
                                "프로젝트 문서 업로드",
                                "PM이 초기 문서를 업로드할 수 있다.",
                                "UNSPECIFIED",
                                document.getId()
                        )),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode()
                )
        );

        assertThat(response.analysisResultId()).isNotNull();
        assertThat(response.requirementsCount()).isEqualTo(1);
        ProjectDocumentAnalysisResult analysisResult = analysisResultRepository.findById(response.analysisResultId()).orElseThrow();
        assertThat(analysisResult.getAgentExecutionId()).isEqualTo("analysis-20260713-001");
        ProjectRequirement requirement = projectRequirementRepository.findAll().getFirst();
        assertThat(requirement.getPriority()).isEqualTo(RequirementPriority.UNSPECIFIED);
        assertThat(requirement.getStatus()).isEqualTo(RequirementStatus.UNCONFIRMED);
        ProjectDocument refreshedDocument = projectDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(refreshedDocument.getStatus()).isEqualTo(ProjectDocumentStatus.ANALYZED);
    }

    @Test
    void saveAnalysisResultFailWhenAgentExecutionIdDuplicated() {
        ProjectDocument document = uploadDocument();
        SaveDocumentAnalysisResultRequest request = new SaveDocumentAnalysisResultRequest(
                "analysis-20260713-001",
                "document-agent-v1",
                "AI 기반 프로젝트 관리 플랫폼 구축",
                "프로젝트 생성 및 관리 자동화",
                List.of(new DocumentAnalysisRequirementRequest(
                        1L,
                        "FUNCTIONAL",
                        "프로젝트 문서 업로드",
                        "PM이 초기 문서를 업로드할 수 있다.",
                        "HIGH",
                        document.getId()
                )),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode()
        );

        projectDocumentAnalysisService.saveAnalysisResult(document.getProject().getId(), request);

        assertThatThrownBy(() -> projectDocumentAnalysisService.saveAnalysisResult(document.getProject().getId(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("duplicate agent execution id");
    }

    @Test
    void saveAnalysisResultFailWhenSourceDocumentIdInvalidAndRollback() {
        ProjectDocument document = uploadDocument();

        assertThatThrownBy(() -> projectDocumentAnalysisService.saveAnalysisResult(
                document.getProject().getId(),
                new SaveDocumentAnalysisResultRequest(
                        "analysis-20260713-001",
                        "document-agent-v1",
                        "AI 기반 프로젝트 관리 플랫폼 구축",
                        "프로젝트 생성 및 관리 자동화",
                        List.of(
                                new DocumentAnalysisRequirementRequest(
                                        1L,
                                        "FUNCTIONAL",
                                        "프로젝트 문서 업로드",
                                        "PM이 초기 문서를 업로드할 수 있다.",
                                        "HIGH",
                                        document.getId()
                                ),
                                new DocumentAnalysisRequirementRequest(
                                        2L,
                                        "FUNCTIONAL",
                                        "잘못된 문서 참조",
                                        "존재하지 않는 문서를 참조한다.",
                                        "HIGH",
                                        99999L
                                )
                        ),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode(),
                        objectMapper.createArrayNode()
                )
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("document not found");

        assertThat(analysisResultRepository.count()).isZero();
        assertThat(projectRequirementRepository.count()).isZero();
        ProjectDocument refreshedDocument = projectDocumentRepository.findById(document.getId()).orElseThrow();
        assertThat(refreshedDocument.getStatus()).isEqualTo(ProjectDocumentStatus.UPLOADED);
    }

    private ProjectDocument uploadDocument() {
        Long projectId = projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project",
                "draft description",
                "PM001",
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        )).projectId();

        projectDocumentService.uploadInitialDocuments(
                projectId,
                List.of(new MockMultipartFile("files", "requirements.txt", "text/plain", "hello".getBytes()))
        );

        return projectDocumentRepository.findAll().getFirst();
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
