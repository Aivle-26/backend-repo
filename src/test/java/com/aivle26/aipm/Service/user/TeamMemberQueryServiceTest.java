package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.user.SaveUserCapabilitiesRequest;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserCapabilityProfile;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TeamMemberQueryServiceTest {

    @MockitoBean
    private S3Client s3Client;

    @Autowired
    private TeamMemberQueryService teamMemberQueryService;

    @Autowired
    private UserCapabilityService userCapabilityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCapabilityProfileRepository capabilityProfileRepository;

    @BeforeEach
    void setUp() {
        capabilityProfileRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(user("STAFF002", "No Capability", "STAFF", UserStatus.ACTIVE));
        userRepository.save(user("STAFF001", "Backend Developer", "STAFF", UserStatus.ACTIVE));
        userRepository.save(user("STAFF003", "Inactive Member", "STAFF", UserStatus.INACTIVE));
        userRepository.save(user("PM001", "Project Manager", "PM", UserStatus.ACTIVE));

        userCapabilityService.save("STAFF001", new SaveUserCapabilitiesRequest(
                List.of("backend", "devops"),
                List.of(
                        new SaveUserCapabilitiesRequest.Skill("java", 4, 36),
                        new SaveUserCapabilitiesRequest.Skill("spring_boot", 5, 30)
                )
        ));
    }

    @Test
    void returnsAllActiveStaffIncludingMembersWithoutCapabilities() {
        var members = teamMemberQueryService.getAllActiveTeamMembers();

        assertThat(members)
                .extracting(member -> member.employeeNumber())
                .containsExactly("STAFF001", "STAFF002");

        var registered = members.getFirst();
        assertThat(registered.capabilityRegistered()).isTrue();
        assertThat(registered.roles()).containsExactly("BACKEND", "DEVOPS");
        assertThat(registered.skills())
                .extracting(skill -> skill.skillCode())
                .containsExactly("JAVA", "SPRING_BOOT");

        var unregistered = members.getLast();
        assertThat(unregistered.capabilityRegistered()).isFalse();
        assertThat(unregistered.roles()).isEmpty();
        assertThat(unregistered.skills()).isEmpty();
    }

    @Test
    void treatsProfileWithoutRolesAsUnregistered() {
        // 역할이 없는 프로필은 담당자 추천 후보에서 제외되므로(PlanningResourceContextAssembler),
        // 화면에도 "미등록"으로 보여야 한다. 그렇지 않으면 "등록됨인데 추천에 안 나온다"가 된다.
        UserCapabilityProfile emptyRoles = new UserCapabilityProfile();
        emptyRoles.setUser(userRepository.findById("STAFF002").orElseThrow());
        emptyRoles.setRoles(new LinkedHashSet<>());
        capabilityProfileRepository.save(emptyRoles);

        var members = teamMemberQueryService.getAllActiveTeamMembers();
        var staff002 = members.stream()
                .filter(member -> member.employeeNumber().equals("STAFF002"))
                .findFirst()
                .orElseThrow();

        assertThat(staff002.capabilityRegistered()).isFalse();
        assertThat(staff002.roles()).isEmpty();
    }

    private User user(
            String employeeNumber,
            String name,
            String role,
            UserStatus status
    ) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        user.setEmail(employeeNumber.toLowerCase() + "@example.com");
        user.setPassword("encoded-password");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
