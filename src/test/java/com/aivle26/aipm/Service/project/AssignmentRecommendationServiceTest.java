package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.AssignmentRecommendationRequest;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationResponse;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendRequest;
import com.aivle26.aipm.Dto.project.PlanningResourceRecommendResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserSkill;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.client.ai.PlanningResourceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserCapabilityProfileRepository capabilityProfileRepository;
    @Mock private PlanningResourceClient planningResourceClient;

    private AssignmentRecommendationService service;

    private Project project;
    private ProjectWbsTask task;
    private ProjectSchedule schedule;
    private User registeredUser;
    private UserCapabilityProfile profile;

    @BeforeEach
    void setUp() {
        PlanningResourceContextAssembler contextAssembler =
                new PlanningResourceContextAssembler(
                        projectRepository,
                        wbsTaskRepository,
                        scheduleRepository,
                        projectMemberRepository,
                        capabilityProfileRepository
                );
        service = new AssignmentRecommendationService(
                projectAuthorizationService,
                contextAssembler,
                planningResourceClient
        );

        project = new Project();
        project.setId(101L);
        project.setName("Test Project");
        project.setPm(user("PM001", "Project Manager"));

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

        when(projectRepository.findWithPmById(101L)).thenReturn(Optional.of(project));
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(101L))
                .thenReturn(List.of(task));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(101L))
                .thenReturn(List.of(schedule));
        when(planningResourceClient.recommendAssignments(any()))
                .thenReturn(aiResponse());
    }

    @Test
    void selectedCandidateUsesDefaultAvailabilityAndMapsAiIdBackToUser() {
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(101L))
                .thenReturn(List.of(member(registeredUser, 28.0)));
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
                .isEqualTo(28.0);
        assertThat(aiMember.allocations().getFirst().allocationStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(aiMember.allocations().getFirst().allocationEndDate())
                .isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(captor.getValue().projectMembers())
                .extracting(PlanningResourceRecommendRequest.ProjectMember::memberName)
                .containsExactly("Backend Developer", "Project Manager");
        assertThat(captor.getValue().projectMembers().getLast().roles())
                .containsExactly("PM");
        assertThat(captor.getValue().projectMembers().getLast().allocations().getFirst()
                .availableHoursPerWeek()).isEqualTo(32.0);
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
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(101L))
                .thenReturn(List.of(
                        member(registeredUser, 28.0),
                        member(unregistered, 24.0)
                ));
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
                .containsExactly("STAFF001", "PM001");
        assertThat(response.candidates().getFirst().availableHoursPerWeek())
                .isEqualTo(28.0);
    }

    @Test
    void removesAssignedWbsFromAiUnassignedList() {
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(101L))
                .thenReturn(List.of(member(registeredUser, 28.0)));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of(profile));
        when(planningResourceClient.recommendAssignments(any()))
                .thenReturn(new PlanningResourceRecommendResponse(
                        101L,
                        aiResponse().requiredStaffing(),
                        aiResponse().assignments(),
                        6.0, 48.0, 0.3,
                        List.of(3L),
                        List.of(),
                        "SUCCEEDED"
                ));

        var response = service.recommend(101L, new AssignmentRecommendationRequest(List.of()));

        assertThat(response.assignments()).extracting(AssignmentRecommendationResponse.Assignment::wbsId)
                .containsExactly(3L);
        assertThat(response.unassignedWbsIds()).isEmpty();
    }

    @Test
    void treatsAssignmentWithoutRecommendedMemberAsUnassigned() {
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(101L))
                .thenReturn(List.of(member(registeredUser, 28.0)));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of(profile));
        PlanningResourceRecommendResponse.Assignment emptyAssignment =
                new PlanningResourceRecommendResponse.Assignment(
                        3L, "BACKEND", List.of(), 6.0, 48.0, 0.3, 1,
                        List.of(), "No suitable member"
                );
        when(planningResourceClient.recommendAssignments(any()))
                .thenReturn(new PlanningResourceRecommendResponse(
                        101L,
                        aiResponse().requiredStaffing(),
                        List.of(emptyAssignment),
                        6.0, 48.0, 0.3,
                        List.of(),
                        List.of(),
                        "SUCCEEDED"
                ));

        var response = service.recommend(101L, new AssignmentRecommendationRequest(List.of()));

        assertThat(response.assignments()).isEmpty();
        assertThat(response.unassignedWbsIds()).containsExactly(3L);
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

    private ProjectMember member(User user, double availableHoursPerWeek) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setAvailableHoursPerWeek(availableHoursPerWeek);
        member.setActive(true);
        member.setSelectedBy("PM001");
        return member;
    }
}
