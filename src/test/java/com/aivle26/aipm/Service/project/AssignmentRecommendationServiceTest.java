package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignmentRecommendationRequest;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserSkill;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentRecommendationServiceTest {

    @Mock private ProjectAuthorizationService projectAuthorizationService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectWbsTaskRepository wbsTaskRepository;
    @Mock private ProjectScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserCapabilityProfileRepository capabilityProfileRepository;
    @Mock private PlanningResourceClient planningResourceClient;

    @InjectMocks
    private AssignmentRecommendationService service;

    private Project project;
    private ProjectWbsTask task;
    private ProjectSchedule schedule;
    private User registeredUser;
    private UserCapabilityProfile profile;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(101L);

        task = new ProjectWbsTask();
        task.setId(3L);
        task.setTaskName("API implementation");
        task.setDescription("Integrate the AI API");
        task.setOrderIndex(1);
        task.setConfirmed(true);

        schedule = new ProjectSchedule();
        schedule.setWbsTask(task);
        schedule.setStartDate(LocalDate.of(2026, 8, 10));
        schedule.setEndDate(LocalDate.of(2026, 8, 17));

        registeredUser = user("STAFF001", "Backend Developer");
        profile = profile(registeredUser, "BACKEND", "JAVA", 4, 36);

        when(projectRepository.findById(101L)).thenReturn(Optional.of(project));
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(101L))
                .thenReturn(List.of(task));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(101L))
                .thenReturn(List.of(schedule));
        when(planningResourceClient.recommendAssignments(any()))
                .thenReturn(aiResponse());
    }

    @Test
    void selectedCandidateUsesDefaultAvailabilityAndMapsAiIdBackToUser() {
        when(userRepository.findAllByEmployeeNumberInAndRoleAndStatus(any(), any(), any()))
                .thenReturn(List.of(registeredUser));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of(profile));

        var response = service.recommend(101L, new AssignmentRecommendationRequest(
                List.of(new AssignmentRecommendationRequest.Candidate("STAFF001", null))
        ));

        ArgumentCaptor<PlanningResourceRecommendRequest> captor =
                ArgumentCaptor.forClass(PlanningResourceRecommendRequest.class);
        org.mockito.Mockito.verify(planningResourceClient)
                .recommendAssignments(captor.capture());
        var aiMember = captor.getValue().projectMembers().getFirst();

        assertThat(aiMember.projectMemberId()).isEqualTo(1L);
        assertThat(aiMember.allocations().getFirst().availableHoursPerWeek())
                .isEqualTo(32.0);
        assertThat(aiMember.allocations().getFirst().allocationStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(aiMember.allocations().getFirst().allocationEndDate())
                .isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(response.candidateMode())
                .isEqualTo(AssignmentRecommendationResponse.CandidateMode.SELECTED);
        assertThat(response.assignments().getFirst().recommendedMembers().getFirst().employeeNumber())
                .isEqualTo("STAFF001");
        assertThat(response.assignments().getFirst().recommendedMembers().getFirst().name())
                .isEqualTo("Backend Developer");
    }

    @Test
    void emptySelectionUsesAllRegisteredActiveStaffOnly() {
        User unregistered = user("STAFF002", "No Capabilities");
        when(userRepository.findAllByRoleAndStatusOrderByNameAscEmployeeNumberAsc(any(), any()))
                .thenReturn(List.of(registeredUser, unregistered));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of(profile));

        var response = service.recommend(
                101L,
                new AssignmentRecommendationRequest(List.of())
        );

        assertThat(response.candidateMode())
                .isEqualTo(AssignmentRecommendationResponse.CandidateMode.ALL);
        assertThat(response.candidates())
                .extracting(AssignmentRecommendationResponse.Candidate::employeeNumber)
                .containsExactly("STAFF001");
        assertThat(response.candidates().getFirst().availableHoursPerWeek())
                .isEqualTo(32.0);
    }

    private PlanningResourceRecommendResponse aiResponse() {
        return new PlanningResourceRecommendResponse(
                101L,
                List.of(new PlanningResourceRecommendResponse.RequiredStaffing(
                        "BACKEND", 1, 1, 0, 6.0, 0.3
                )),
                List.of(new PlanningResourceRecommendResponse.Assignment(
                        3L,
                        "BACKEND",
                        List.of(new PlanningResourceRecommendResponse.RequiredSkill("JAVA", 3)),
                        6.0,
                        48.0,
                        0.3,
                        1,
                        List.of(new PlanningResourceRecommendResponse.RecommendedMember(
                                1L, 92.0, 48.0, 16.0
                        )),
                        "The role, skills, and availability are suitable."
                )),
                6.0,
                48.0,
                0.3,
                List.of(),
                List.of(),
                "SUCCEEDED"
        );
    }

    private User user(String employeeNumber, String name) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        return user;
    }

    private UserCapabilityProfile profile(
            User user,
            String role,
            String skillCode,
            int proficiencyLevel,
            int experienceMonths
    ) {
        UserSkill skill = new UserSkill();
        skill.setSkillCode(skillCode);
        skill.setProficiencyLevel(proficiencyLevel);
        skill.setExperienceMonths(experienceMonths);

        UserCapabilityProfile profile = new UserCapabilityProfile();
        profile.setEmployeeNumber(user.getEmployeeNumber());
        profile.setUser(user);
        profile.setRoles(new LinkedHashSet<>(List.of(role)));
        profile.setSkills(List.of(skill));
        return profile;
    }
}
