package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.CreateProjectDraftResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectDocumentAnalysisResultRepository;
import com.aivle26.aipm.Repository.project.ProjectDocumentRepository;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsResultRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.project.WeeklyScrumReportRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@WithMockUser(username = "PM001", roles = "PM")
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectCreationService projectCreationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectRequiredArtifactRepository requiredArtifactRepository;

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
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private WeeklyScrumReportRepository weeklyScrumReportRepository;

    @BeforeEach
    void setUp() {
        weeklyScrumReportRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectScheduleRepository.deleteAll();
        projectScheduleResultRepository.deleteAll();
        projectWbsTaskRepository.deleteAll();
        projectWbsResultRepository.deleteAll();
        projectRequirementRepository.deleteAll();
        analysisResultRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        requiredArtifactRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createProjectDraftSuccess() {
        userRepository.save(createPmUser("PM001"));

        CreateProjectDraftResponse response = projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project",
                "draft description",
                null,
                "PM001",
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        ));

        assertThat(response.projectId()).isNotNull();
        assertThat(response.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(response.pmEmployeeNumber()).isEqualTo("PM001");
        assertThat(projectRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "PM404", roles = "PM")
    void createProjectDraftFailWhenPmUserMissing() {
        assertThatThrownBy(() -> projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project",
                "draft description",
                null,
                "PM404",
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("pm user not found");

        assertThat(projectRepository.count()).isZero();
    }

    @Test
    void listProjectsReturnsOnlyProjectsOwnedByCurrentPm() {
        User currentPm = userRepository.save(createPmUser("PM001"));
        User otherPm = userRepository.save(createPmUser("PM002"));
        projectCreationService.createProjectDraft(new CreateProjectDraftRequest(
                "New PM Project",
                "draft description",
                null,
                "PM001",
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 31)
        ));
        projectRepository.save(createProject("Other PM Project", otherPm));

        var projects = projectService.listProjects(new AuthenticatedUser(
                currentPm.getEmployeeNumber(),
                "PM"
        ));

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).name()).isEqualTo("New PM Project");
        assertThat(projects.get(0).description()).isEqualTo("draft description");
        assertThat(projects.get(0).pmEmployeeNumber()).isEqualTo("PM001");
        assertThat(projects.get(0).status()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    void listProjectsReturnsOnlyProjectsParticipatedInByCurrentStaff() {
        User firstPm = userRepository.save(createPmUser("PM001"));
        User secondPm = userRepository.save(createPmUser("PM002"));
        Project participatingProject =
                projectRepository.save(createProject("Participating Project", firstPm));
        projectRepository.save(createProject("Hidden Project", secondPm));

        User staff = userRepository.save(createStaffUser("STAFF001"));
        ProjectMember membership = new ProjectMember();
        membership.setProject(participatingProject);
        membership.setUser(staff);
        membership.setAvailableHoursPerWeek(32.0);
        membership.setActive(true);
        membership.setSelectedBy(firstPm.getEmployeeNumber());
        projectMemberRepository.save(membership);

        var projects = projectService.listProjects(
                new AuthenticatedUser("STAFF001", "STAFF")
        );

        assertThat(projects)
                .extracting(project -> project.name())
                .containsExactly("Participating Project");
    }

    private Project createProject(String name, User pm) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("draft description");
        project.setPm(pm);
        project.setStatus(ProjectStatus.DRAFT);
        project.setPlannedStartDate(LocalDate.of(2026, 7, 13));
        project.setPlannedEndDate(LocalDate.of(2026, 7, 31));
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

    private User createStaffUser(String employeeNumber) {
        User user = createPmUser(employeeNumber);
        user.setName("Project Staff");
        user.setRole("STAFF");
        return user;
    }
}
