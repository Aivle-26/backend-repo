package com.aivle26.aipm.Service.auth;

import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Repository.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(createUser("PM001"));
    }

    @Test
    void refreshDoesNotExtendAbsoluteExpiration() {
        User user = userRepository.findById("PM001").orElseThrow();
        AuthSessionResponse issued = authService.issueSession(user);

        AuthSessionResponse refreshed = authService.refresh(issued.refreshToken());

        assertThat(refreshed.absoluteExpiresAt()).isEqualTo(issued.absoluteExpiresAt());
        assertThat(refreshed.accessTokenExpiresAt()).isLessThanOrEqualTo(issued.absoluteExpiresAt());
    }

    @Test
    void logoutByRefreshTokenClearsStoredSession() {
        AuthSessionResponse issued = authService.issueSession(userRepository.findById("PM001").orElseThrow());

        authService.logoutByRefreshToken(issued.refreshToken());

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(savedUser.getRefreshTokenHash()).isNull();
        assertThat(savedUser.getLoginAt()).isNull();
        assertThat(savedUser.getAbsoluteExpiresAt()).isNull();
        assertThat(savedUser.getLastActivityAt()).isNull();
    }

    private User createUser(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Project Manager");
        user.setEmail("pm001@example.com");
        user.setPassword("$2a$10$V2M5q8sz6r3Wn8A6VJvQ6.6g8g6R/0nYw1HnY2a0mJzP0M4Kp8XyK");
        user.setRole("PM");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }
}
