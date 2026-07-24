package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.ProjectArtifactStatusResponse;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ArtifactCheckStatus;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Entity.ProjectRequiredArtifact;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@WithMockUser(username = "PM001", roles = "PM")
class ProjectArtifactStatusServiceTest {

    @Autowired
    private ProjectArtifactStatusService artifactStatusService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectRequiredArtifactRepository requiredArtifactRepository;

    @Autowired
    private ProjectArtifactRepository artifactRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(createUser("PM001"));
        project = projectRepository.saveAndFlush(createProject(owner));
    }

    @Test
    void returnsCompletedWhenAllArtifactsMeetVersionAndApproval() {
        saveRequired(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements", "1.0");
        saveRequired(ProjectArtifactType.ERD, "ERD", "2.0");
        saveArtifact(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements", "1.0", ArtifactApprovalStatus.APPROVED);
        saveArtifact(ProjectArtifactType.ERD, "ERD", "2.1", ArtifactApprovalStatus.APPROVED);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.totalRequiredCount()).isEqualTo(2);
        assertThat(response.registeredCount()).isEqualTo(2);
        assertThat(response.approvedCount()).isEqualTo(2);
        assertThat(response.registrationRate()).isEqualTo(100.0);
        assertThat(response.approvalCompletionRate()).isEqualTo(100.0);
        assertThat(response.artifactRegister())
                .allMatch(item -> item.status() == ArtifactCheckStatus.COMPLETED);
        assertThat(response.recommendations())
                .containsExactly("All required artifacts are registered and approved.");
    }

    @Test
    void reportsPartiallyMissingArtifacts() {
        saveRequired(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements", "1.0");
        saveRequired(ProjectArtifactType.ERD, "ERD", "1.0");
        saveArtifact(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements", "1.0", ArtifactApprovalStatus.APPROVED);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.registeredCount()).isEqualTo(1);
        assertThat(response.approvedCount()).isEqualTo(1);
        assertThat(response.registrationRate()).isEqualTo(50.0);
        assertThat(response.missingArtifacts())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.artifactType()).isEqualTo(ProjectArtifactType.ERD);
                    assertThat(item.status()).isEqualTo(ArtifactCheckStatus.MISSING);
                });
    }

    @Test
    void reportsOutdatedArtifact() {
        saveRequired(ProjectArtifactType.FUNCTION_SPECIFICATION, "Function Specification", "1.10");
        saveArtifact(ProjectArtifactType.FUNCTION_SPECIFICATION, "Function Specification", "1.2", ArtifactApprovalStatus.APPROVED);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.approvedCount()).isZero();
        assertThat(response.outdatedArtifacts()).hasSize(1);
        assertThat(response.artifactRegister().get(0).status()).isEqualTo(ArtifactCheckStatus.INCOMPLETE);
    }

    @Test
    void reportsPendingApproval() {
        saveRequired(ProjectArtifactType.UI_DESIGN, "UI Design", "1.0");
        saveArtifact(ProjectArtifactType.UI_DESIGN, "UI Design", "1.0", ArtifactApprovalStatus.PENDING);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.unapprovedArtifacts()).hasSize(1);
        assertThat(response.outdatedArtifacts()).isEmpty();
        assertThat(response.approvalCompletionRate()).isEqualTo(0.0);
    }

    @Test
    void includesArtifactInBothOutdatedAndUnapprovedLists() {
        saveRequired(ProjectArtifactType.ERD, "ERD", "2.0");
        saveArtifact(ProjectArtifactType.ERD, "ERD", "1.0", ArtifactApprovalStatus.PENDING);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.outdatedArtifacts()).hasSize(1);
        assertThat(response.unapprovedArtifacts()).hasSize(1);
        assertThat(response.recommendations()).hasSize(2);
    }

    @Test
    void returnsZeroRatesWhenNoRequiredArtifactsExist() {
        saveArtifact(ProjectArtifactType.ERD, "Optional ERD", "1.0", ArtifactApprovalStatus.APPROVED);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.totalRequiredCount()).isZero();
        assertThat(response.registeredCount()).isZero();
        assertThat(response.approvedCount()).isZero();
        assertThat(response.registrationRate()).isEqualTo(0.0);
        assertThat(response.approvalCompletionRate()).isEqualTo(0.0);
        assertThat(response.artifactRegister()).isEmpty();
    }

    @Test
    void reportsAllArtifactsMissingWhenNoArtifactsAreRegistered() {
        saveRequired(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements", "1.0");
        saveRequired(ProjectArtifactType.ERD, "ERD", "1.0");

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.registeredCount()).isZero();
        assertThat(response.missingArtifacts()).hasSize(2);
        assertThat(response.artifactRegister())
                .allMatch(item -> item.status() == ArtifactCheckStatus.MISSING);
    }

    @Test
    void selectsVersionOneTenOverOneTwo() {
        saveRequired(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements", "1.2");
        saveArtifact(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements 1.2", "1.2", ArtifactApprovalStatus.PENDING);
        saveArtifact(ProjectArtifactType.REQUIREMENTS_DEFINITION, "Requirements 1.10", "1.10", ArtifactApprovalStatus.APPROVED);

        ProjectArtifactStatusResponse response = artifactStatusService.getStatus(project.getId());

        assertThat(response.artifactRegister())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.version()).isEqualTo("1.10");
                    assertThat(item.status()).isEqualTo(ArtifactCheckStatus.COMPLETED);
                });
    }

    @Test
    void rejectsMissingProjectWithExistingNotFoundRule() {
        assertThatThrownBy(() -> artifactStatusService.getStatus(Long.MAX_VALUE))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessage("project not found");
    }

    @Test
    @WithMockUser(username = "PM002", roles = "PM")
    void rejectsProjectOwnedByAnotherPm() {
        userRepository.save(createUser("PM002"));

        assertThatThrownBy(() -> artifactStatusService.getStatus(project.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access is denied");
    }

    private void saveRequired(ProjectArtifactType type, String name, String requiredVersion) {
        ProjectRequiredArtifact requiredArtifact = new ProjectRequiredArtifact();
        requiredArtifact.setProject(project);
        requiredArtifact.setArtifactType(type);
        requiredArtifact.setArtifactName(name);
        requiredArtifact.setRequiredVersion(requiredVersion);
        requiredArtifactRepository.saveAndFlush(requiredArtifact);
    }

    private void saveArtifact(
            ProjectArtifactType type,
            String name,
            String version,
            ArtifactApprovalStatus approvalStatus
    ) {
        ProjectArtifact artifact = new ProjectArtifact();
        artifact.setProject(project);
        artifact.setArtifactType(type);
        artifact.setArtifactName(name);
        artifact.setVersion(version);
        artifact.setApprovalStatus(approvalStatus);
        artifactRepository.saveAndFlush(artifact);
    }

    private Project createProject(User owner) {
        Project savedProject = new Project();
        savedProject.setName("Artifact Project");
        savedProject.setDescription("artifact status test");
        savedProject.setPm(owner);
        savedProject.setStatus(ProjectStatus.DRAFT);
        savedProject.setPlannedStartDate(LocalDate.of(2026, 7, 1));
        savedProject.setPlannedEndDate(LocalDate.of(2026, 8, 1));
        return savedProject;
    }

    private User createUser(String employeeNumber) {
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
