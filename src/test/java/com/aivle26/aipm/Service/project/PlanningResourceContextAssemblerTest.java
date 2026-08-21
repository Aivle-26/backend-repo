package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectSchedule;
import com.aivle26.aipm.Entity.project.ProjectWbsTask;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectScheduleRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsTaskRepository;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningResourceContextAssemblerTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectWbsTaskRepository wbsTaskRepository;
    @Mock private ProjectScheduleRepository scheduleRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserCapabilityProfileRepository capabilityProfileRepository;

    private PlanningResourceContextAssembler assembler;
    private Project project;
    private ProjectWbsTask task;
    private ProjectSchedule schedule;
    private User pm;
    private User staff;

    @BeforeEach
    void setUp() {
        assembler = new PlanningResourceContextAssembler(
                projectRepository,
                wbsTaskRepository,
                scheduleRepository,
                projectMemberRepository,
                capabilityProfileRepository
        );
        pm = user("PM001", "Project Manager");
        staff = user("STAFF001", "Backend Developer");
        project = new Project();
        project.setId(1L);
        project.setName("Organization Project");
        project.setPm(pm);

        task = new ProjectWbsTask();
        task.setId(10L);
        task.setTaskName("Implement API");
        task.setOrderIndex(1);
        task.setConfirmed(true);

        schedule = new ProjectSchedule();
        schedule.setWbsTask(task);
        schedule.setStartDate(LocalDate.of(2026, 8, 5));
        schedule.setEndDate(LocalDate.of(2026, 8, 12));

        when(projectRepository.findWithPmById(1L)).thenReturn(Optional.of(project));
    }

    @Test
    void rejectsMissingConfirmedWbs() {
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(1L)).thenReturn(List.of());

        assertApiError("CONFIRMED_WBS_NOT_FOUND");
    }

    @Test
    void rejectsMissingSchedule() {
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(1L)).thenReturn(List.of(task));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(1L))
                .thenReturn(List.of());

        assertApiError("PLANNING_SCHEDULE_NOT_FOUND");
    }

    @Test
    void recommendationCanUseProjectManagerWhenNoActiveMemberExists() {
        arrangeScheduledTask();
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of());
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of());

        var context = assembler.assembleForRecommendation(1L, null);

        assertThat(context.aiRequest().projectMembers()).singleElement().satisfies(member -> {
            assertThat(member.memberName()).isEqualTo("Project Manager");
            assertThat(member.roles()).containsExactly("PM");
            assertThat(member.allocations().getFirst().availableHoursPerWeek()).isEqualTo(32.0);
        });
    }

    @Test
    void keepsMemberWithoutCapabilitiesAsUnknownForOrganizationChart() {
        arrangeScheduledTask();
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of(member(staff)));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of());

        var context = assembler.assembleForOrganizationChart(1L);

        assertThat(context.aiRequest().projectMembers()).hasSize(2);
        assertThat(context.aiRequest().projectMembers())
                .filteredOn(member -> member.memberName().equals("Backend Developer"))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.roles()).isEmpty();
                    assertThat(member.skills()).isEmpty();
                });
        assertThat(context.aiRequest().projectMembers())
                .filteredOn(member -> member.memberName().equals("Project Manager"))
                .singleElement()
                .extracting(member -> member.roles())
                .isEqualTo(List.of("PM"));
    }

    @Test
    void rejectsOrganizationChartWithoutActiveMembers() {
        arrangeScheduledTask();
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of());

        assertApiError("ACTIVE_PROJECT_MEMBER_NOT_FOUND");
    }

    @Test
    void keepsAllEightMembersWhenEveryCapabilityIsRegistered() {
        var context = assembleEightMembersWithKnownCapabilityCount(8);

        assertThat(context.aiRequest().projectMembers()).hasSize(8);
        assertThat(context.aiRequest().projectMembers())
                .allSatisfy(member -> assertThat(member.roles()).isNotEmpty());
    }

    @Test
    void keepsOneUnknownMemberWithoutInventingCapabilities() {
        var context = assembleEightMembersWithKnownCapabilityCount(7);

        assertUnknownCapabilityCount(context, 1);
    }

    @Test
    void keepsFourUnknownMembersWithoutInventingCapabilities() {
        var context = assembleEightMembersWithKnownCapabilityCount(4);

        assertUnknownCapabilityCount(context, 4);
    }

    @Test
    void keepsActualNamesAndAddsProjectManagerToTemporaryAiMapping() {
        arrangeScheduledTask();
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(List.of(member(staff)));
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(List.of(profile(staff)));

        var context = assembler.assembleForOrganizationChart(1L);

        assertThat(context.aiRequest().projectName()).isEqualTo("Organization Project");
        assertThat(context.aiRequest().projectMembers())
                .extracting(member -> member.memberName())
                .containsExactly("Backend Developer", "Project Manager");
        assertThat(context.projectManagerAiId()).isEqualTo(2L);
        assertThat(context.candidatesByAiId().get(2L).availableHoursPerWeek())
                .isZero();
    }

    private void arrangeScheduledTask() {
        when(wbsTaskRepository.findByProjectIdAndConfirmedTrue(1L)).thenReturn(List.of(task));
        when(scheduleRepository.findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(1L))
                .thenReturn(List.of(schedule));
    }

    private PlanningResourceContextAssembler.PlanningResourceContext
            assembleEightMembersWithKnownCapabilityCount(int knownCapabilityCount) {
        arrangeScheduledTask();
        List<User> users = new java.util.ArrayList<>();
        users.add(pm);
        IntStream.rangeClosed(1, 7)
                .mapToObj(index -> user("STAFF%03d".formatted(index), "Staff " + index))
                .forEach(users::add);
        List<ProjectMember> members = users.stream().map(this::member).toList();
        List<UserCapabilityProfile> profiles = users.stream()
                .limit(knownCapabilityCount)
                .map(this::profile)
                .toList();
        when(projectMemberRepository
                .findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(1L))
                .thenReturn(members);
        when(capabilityProfileRepository.findAllByEmployeeNumberIn(any()))
                .thenReturn(profiles);

        return assembler.assembleForOrganizationChart(1L);
    }

    private void assertUnknownCapabilityCount(
            PlanningResourceContextAssembler.PlanningResourceContext context,
            long expectedCount
    ) {
        assertThat(context.aiRequest().projectMembers()).hasSize(8);
        assertThat(context.aiRequest().projectMembers().stream()
                .filter(member -> member.roles().isEmpty())
                .count()).isEqualTo(expectedCount);
        assertThat(context.aiRequest().projectMembers())
                .filteredOn(member -> member.roles().isEmpty())
                .allSatisfy(member -> assertThat(member.skills()).isEmpty());
    }

    private void assertApiError(String code) {
        assertThatThrownBy(() -> assembler.assembleForOrganizationChart(1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private User user(String employeeNumber, String name) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        return user;
    }

    private ProjectMember member(User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setAvailableHoursPerWeek(32.0);
        member.setActive(true);
        return member;
    }

    private UserCapabilityProfile profile(User user) {
        UserCapabilityProfile profile = new UserCapabilityProfile();
        profile.setUser(user);
        profile.setEmployeeNumber(user.getEmployeeNumber());
        profile.setRoles(new LinkedHashSet<>(List.of("BACKEND")));
        profile.setSkills(List.of());
        return profile;
    }
}
