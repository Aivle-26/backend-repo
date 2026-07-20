package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.PasswordEmailSendRequest;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.UserRepository;
import jakarta.mail.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(createUser("PM001"));
        given(javaMailSender.createMimeMessage()).willReturn(new jakarta.mail.internet.MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendPasswordEmailCodeSuccess() {
        userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com"));

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(savedUser.getVerificationEmail()).isEqualTo("pm001@example.com");
        assertThat(savedUser.getVerificationCode()).matches("\\d{6}");
        assertThat(savedUser.getVerificationCodeExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(savedUser.getVerificationCodeFailedAttempts()).isZero();
    }

    @Test
    void sendPasswordEmailCodeFailWhenResentTooSoon() {
        userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com"));

        assertThatThrownBy(() -> userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com")))
                .isInstanceOf(ApiException.class)
                .hasMessage("verification code resend too soon");
    }

    @Test
    void verifyPasswordEmailCodeInvalidatesAfterFiveFailures() {
        userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com"));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> userService.verifyPasswordEmailCode(new PasswordEmailCheckRequest("PM001", "pm001@example.com", "000000")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("verification code mismatch");
        }

        assertThatThrownBy(() -> userService.verifyPasswordEmailCode(new PasswordEmailCheckRequest("PM001", "pm001@example.com", "000000")))
                .isInstanceOf(ApiException.class)
                .hasMessage("verification code invalidated");

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(savedUser.getVerificationCode()).isNull();
        assertThat(savedUser.getVerificationEmail()).isNull();
        assertThat(savedUser.getVerificationCodeFailedAttempts()).isZero();
    }

    @Test
    void verifyPasswordEmailCodeSuccessClearsVerificationCode() {
        userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com"));
        User issuedUser = userRepository.findById("PM001").orElseThrow();

        var response = userService.verifyPasswordEmailCode(
                new PasswordEmailCheckRequest("PM001", "pm001@example.com", issuedUser.getVerificationCode())
        );

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(response.resetToken()).isEqualTo(savedUser.getResetToken());
        assertThat(savedUser.getEmail()).isEqualTo("pm001@example.com");
        assertThat(savedUser.getVerificationCode()).isNull();
        assertThat(savedUser.getVerificationEmail()).isNull();
        assertThat(savedUser.getVerificationCodeExpiresAt()).isNull();
    }

    private User createUser(String employeeNumber) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Project Manager");
        user.setEmail(null);
        user.setPassword("$2a$10$V2M5q8sz6r3Wn8A6VJvQ6.6g8g6R/0nYw1HnY2a0mJzP0M4Kp8XyK");
        user.setRole("PM");
        user.setStatus(UserStatus.MUST_CHANGE_PASSWORD);
        user.setEmailVerified(false);
        return user;
    }
}
