package com.aivle26.aipm.Config;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.risk.RiskTeamMember;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.risk.RiskTeamMemberRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * local 프로필 전용 초기 데이터.
 *
 * <p>H2 인메모리 DB는 앱을 띄울 때마다 비어 있어서, 시드가 없으면
 * /api/projects/1/... 호출이 전부 404로 막힌다. Slack 연동을 테스트하려면
 * 최소한 PM 사용자 1명과 프로젝트 1개가 있어야 한다.
 *
 * <p>운영(prod)에서는 이 빈이 등록되지 않는다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataSeeder implements CommandLineRunner {

    private static final String PM_EMPLOYEE_NUMBER = "PM-0001";
    private static final String PM_EMAIL = "pm@local.test";
    private static final String PM_PASSWORD = "local1234!";

    private static final String STAFF_EMPLOYEE_NUMBER = "ST-0001";
    private static final String STAFF_EMAIL = "staff@local.test";
    private static final String STAFF_PASSWORD = "local1234!";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final RiskTeamMemberRepository riskTeamMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findById(PM_EMPLOYEE_NUMBER).isPresent()) {
            return;
        }

        User pm = new User();
        pm.setEmployeeNumber(PM_EMPLOYEE_NUMBER);
        pm.setName("로컬 PM");
        pm.setEmail(PM_EMAIL);
        pm.setPassword(passwordEncoder.encode(PM_PASSWORD));
        pm.setRole("PM");
        pm.setStatus(UserStatus.ACTIVE);
        pm.setEmailVerified(true);
        userRepository.save(pm);

        User staff = new User();
        staff.setEmployeeNumber(STAFF_EMPLOYEE_NUMBER);
        staff.setName("로컬 직원");
        staff.setEmail(STAFF_EMAIL);
        staff.setPassword(passwordEncoder.encode(STAFF_PASSWORD));
        staff.setRole("STAFF");
        staff.setStatus(UserStatus.ACTIVE);
        staff.setEmailVerified(true);
        userRepository.save(staff);

        Project project = new Project();
        project.setName("로컬 테스트 프로젝트");
        project.setDescription("Slack 커뮤니케이션 리스크 연동 확인용");
        project.setPm(pm);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setPlannedStartDate(LocalDate.now().minusDays(30));
        project.setPlannedEndDate(LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(project);

        // 담당자 재배정 추천 흐름 검증용 더미 팀원. (배정 도메인 생기면 제거)
        seedDummyTeamMembers(saved.getId());

        log.info("=== local 시드 생성 ===");
        log.info("  PM 계정   : {} / {}", PM_EMAIL, PM_PASSWORD);
        log.info("  직원 계정 : {} / {}", STAFF_EMAIL, STAFF_PASSWORD);
        log.info("  프로젝트 ID: {}", saved.getId());
    }

    /** 재배정·지연분석 테스트용 더미 팀원 4명 (1명은 현재 담당자). */
    private void seedDummyTeamMembers(Long projectId) {
        // 인자: name, role, skills, workloadRate, overdue, current, assigned, completed, inProgress, avgDelayDays, daysSinceUpdate
        riskTeamMemberRepository.save(member(projectId, "김개발", "Backend", "Java", 85, 3, true, 8, 3, 2, 4.0, 6));
        riskTeamMemberRepository.save(member(projectId, "이수현", "Backend", "Java,Spring,AWS", 40, 0, false, 6, 6, 0, 0.0, 1));
        riskTeamMemberRepository.save(member(projectId, "박지민", "Backend", "Java", 60, 1, false, 5, 2, 2, 2.0, 3));
        riskTeamMemberRepository.save(member(projectId, "최유진", "Frontend", "React", 30, 0, false, 4, 3, 1, 1.0, 2));
    }

    private RiskTeamMember member(Long projectId, String name, String role, String skills,
                                  double workloadRate, int overdueTaskCount, boolean currentAssignee,
                                  int assignedTaskCount, int completedTaskCount, int inProgressTaskCount,
                                  double averageDelayDays, int daysSinceLastUpdate) {
        RiskTeamMember m = new RiskTeamMember();
        m.setProjectId(projectId);
        m.setMemberName(name);
        m.setRole(role);
        m.setSkills(skills);
        m.setWorkloadRate(workloadRate);
        m.setOverdueTaskCount(overdueTaskCount);
        m.setCurrentAssignee(currentAssignee);
        m.setAssignedTaskCount(assignedTaskCount);
        m.setCompletedTaskCount(completedTaskCount);
        m.setInProgressTaskCount(inProgressTaskCount);
        m.setAverageDelayDays(averageDelayDays);
        m.setDaysSinceLastUpdate(daysSinceLastUpdate);
        return m;
    }
}
