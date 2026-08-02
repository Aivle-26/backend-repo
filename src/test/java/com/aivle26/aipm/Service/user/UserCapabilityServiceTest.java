package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.user.SaveUserCapabilitiesRequest;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.user.UserCapabilityProfileRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserCapabilityServiceTest {

    @MockitoBean
    private S3Client s3Client;

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

        User user = new User();
        user.setEmployeeNumber("STAFF001");
        user.setName("Backend Developer");
        user.setEmail("staff001@example.com");
        user.setPassword("encoded-password");
        user.setRole("STAFF");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Test
    void savesNormalizedRolesAndSkillsAndReplacesPreviousProfile() {
        var first = userCapabilityService.save("STAFF001", request(
                List.of("backend"),
                List.of(new SaveUserCapabilitiesRequest.Skill("java", 4, 36))
        ));

        assertThat(first.employeeNumber()).isEqualTo("STAFF001");
        assertThat(first.roles()).containsExactly("BACKEND");
        assertThat(first.skills().getFirst().skillCode()).isEqualTo("JAVA");

        var replaced = userCapabilityService.save("STAFF001", request(
                List.of("backend", "devops"),
                List.of(new SaveUserCapabilitiesRequest.Skill("spring_boot", 5, 48))
        ));

        assertThat(capabilityProfileRepository.count()).isEqualTo(1);
        assertThat(replaced.roles()).containsExactlyInAnyOrder("BACKEND", "DEVOPS");
        assertThat(replaced.skills()).hasSize(1);
        assertThat(replaced.skills().getFirst().skillCode()).isEqualTo("SPRING_BOOT");
        assertThat(replaced.skills().getFirst().proficiencyLevel()).isEqualTo(5);
        assertThat(replaced.skills().getFirst().experienceMonths()).isEqualTo(48);
    }

    @Test
    void rejectsDuplicateSkillCodesAfterNormalization() {
        assertThatThrownBy(() -> userCapabilityService.save("STAFF001", request(
                List.of("BACKEND"),
                List.of(
                        new SaveUserCapabilitiesRequest.Skill("java", 3, 12),
                        new SaveUserCapabilitiesRequest.Skill(" JAVA ", 4, 24)
                )
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("duplicate skill code");
    }

    private SaveUserCapabilitiesRequest request(
            List<String> roles,
            List<SaveUserCapabilitiesRequest.Skill> skills
    ) {
        return new SaveUserCapabilitiesRequest(roles, skills);
    }
}
