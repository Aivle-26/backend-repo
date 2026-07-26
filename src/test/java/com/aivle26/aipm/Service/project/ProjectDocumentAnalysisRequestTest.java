package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.client.ai.ProjectAgentClient;
import com.aivle26.aipm.Dto.project.AgentRequestResult;
import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Entity.project.AgentExecutionStatus;
import com.aivle26.aipm.Entity.project.Project;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ProjectDocumentAnalysisRequestTest {
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

    @MockitoBean
    private ProjectAgentClient projectAgentClient;

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
    void requestAnalysisReusesExistingStoredDocumentsWithoutCreatingNewRecords() {
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
        long documentCountBefore = projectDocumentRepository.count();

        AgentRequestResult expected = new AgentRequestResult("request-1", AgentExecutionStatus.REQUESTED, "document-agent-v1");
        when(projectAgentClient.requestDocumentAnalysis(projectId)).thenReturn(expected);

        AgentRequestResult response = projectDocumentAnalysisService.requestAnalysis(projectId);

        assertThat(response).isEqualTo(expected);
        assertThat(projectDocumentRepository.count()).isEqualTo(documentCountBefore);
        verify(projectAgentClient).requestDocumentAnalysis(projectId);
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
