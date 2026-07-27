package com.aivle26.aipm.Config;

import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectStatus;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.UserRepository;
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

        log.info("=== local 시드 생성 ===");
        log.info("  PM 계정   : {} / {}", PM_EMAIL, PM_PASSWORD);
        log.info("  직원 계정 : {} / {}", STAFF_EMAIL, STAFF_PASSWORD);
        log.info("  프로젝트 ID: {}", saved.getId());
    }
}
