package com.aivle26.aipm.Config;

import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectMember;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.project.ProjectMemberRepository;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findById(PM_EMPLOYEE_NUMBER).isPresent()) {
            return;
        }

        User pm = createUser(PM_EMPLOYEE_NUMBER, "로컬 PM", PM_EMAIL, PM_PASSWORD, "PM");
        User staff = createUser(STAFF_EMPLOYEE_NUMBER, "로컬 직원", STAFF_EMAIL, STAFF_PASSWORD, "STAFF");

        Project project = new Project();
        project.setName("로컬 테스트 프로젝트");
        project.setDescription("로컬 연동 확인용 프로젝트");
        project.setPm(pm);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setPlannedStartDate(LocalDate.now().minusDays(30));
        project.setPlannedEndDate(LocalDate.now().plusDays(30));
        Project savedProject = projectRepository.save(project);

        ProjectMember projectMember = new ProjectMember();
        projectMember.setProject(savedProject);
        projectMember.setUser(staff);
        projectMember.setAvailableHoursPerWeek(32.0);
        projectMember.setActive(true);
        projectMember.setSelectedBy(pm.getEmployeeNumber());
        projectMemberRepository.save(projectMember);

        log.info("Local seed created: pm={}, staff={}, projectId={}", PM_EMAIL, STAFF_EMAIL, savedProject.getId());
    }

    private User createUser(
            String employeeNumber,
            String name,
            String email,
            String rawPassword,
            String role
    ) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }
}
