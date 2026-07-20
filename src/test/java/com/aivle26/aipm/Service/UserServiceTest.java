package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.LoginRequest;
import com.aivle26.aipm.Dto.LoginVerifyRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.PasswordEmailSendRequest;
import com.aivle26.aipm.Dto.SignupRequest;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.EmailVerificationRepository;
import com.aivle26.aipm.Repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        emailVerificationRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(createUser("PM001", "pm001@example.com", "PM", "CorrectPassword1!"));
        given(javaMailSender.createMimeMessage()).willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void signupCreatesPmUser() {
        var response = userService.signup(new SignupRequest(
                "PM002",
                "New PM",
                "newpm@example.com",
                "Signup123",
                "PM"
        ));

        User savedUser = userRepository.findById("PM002").orElseThrow();
        assertThat(response.employeeNumber()).isEqualTo("PM002");
        assertThat(response.role()).isEqualTo("PM");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getEmail()).isEqualTo("newpm@example.com");
        assertThat(savedUser.getPassword()).isNotEqualTo("Signup123");
        assertThat(passwordEncoder.matches("Signup123", savedUser.getPassword())).isTrue();
        assertThat(savedUser.isEmailVerified()).isTrue();
    }

    @Test
    void signupCreatesStaffUser() {
        var response = userService.signup(new SignupRequest(
                "ST002",
                "New Staff",
                "newstaff@example.com",
                "Signup123",
                "staff"
        ));

        User savedUser = userRepository.findById("ST002").orElseThrow();
        assertThat(response.role()).isEqualTo("STAFF");
        assertThat(savedUser.getRole()).isEqualTo("STAFF");
    }

    @Test
    void signupFailsForDuplicateEmail() {
        assertThatThrownBy(() -> userService.signup(new SignupRequest(
                "PM999",
                "Duplicate Email",
                "PM001@example.com",
                "Signup123",
                "PM"
        )))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("email already exists");
                });
    }

    @Test
    void signupFailsForInvalidEmail() {
        assertThatThrownBy(() -> userService.signup(new SignupRequest(
                "PM003",
                "Invalid Email",
                "not-an-email",
                "Signup123",
                "PM"
        )))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("invalid email");
                });
    }

    @Test
    void signupFailsForWeakPassword() {
        assertThatThrownBy(() -> userService.signup(new SignupRequest(
                "PM003",
                "Weak Password",
                "weak@example.com",
                "password",
                "PM"
        )))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("password must be 8-72 chars and include letters and numbers");
                });
    }

    @Test
    void signupUserCanLoginAndVerify() throws Exception {
        userService.signup(new SignupRequest(
                "PM003",
                "Login PM",
                "loginpm@example.com",
                "Signup123",
                "PM"
        ));

        var loginResponse = userService.login(new LoginRequest("loginpm@example.com", "Signup123", "PM"));
        String verificationCode = extractLatestVerificationCode();
        var verifyResponse = userService.verifyLogin(new LoginVerifyRequest("loginpm@example.com", verificationCode));

        assertThat(loginResponse.success()).isTrue();
        assertThat(verifyResponse.employeeNumber()).isEqualTo("PM003");
        assertThat(verifyResponse.accessToken()).isNotBlank();
    }

    @Test
    void signupUserLoginFailsForWrongRole() {
        userService.signup(new SignupRequest(
                "ST003",
                "Login Staff",
                "loginstaff@example.com",
                "Signup123",
                "STAFF"
        ));

        assertThatThrownBy(() -> userService.login(new LoginRequest("loginstaff@example.com", "Signup123", "PM")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void loginFailsForUnknownEmailWithoutSendingMail() {
        assertThatThrownBy(() -> userService.login(new LoginRequest("unknown@example.com", "CorrectPassword1!", "PM")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });

        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailVerificationRepository.count()).isZero();
    }

    @Test
    void loginFailsForPasswordMismatchWithoutSendingMail() {
        assertThatThrownBy(() -> userService.login(new LoginRequest("pm001@example.com", "wrong-password", "PM")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });

        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailVerificationRepository.count()).isZero();
    }

    @Test
    void loginFailsForRoleMismatchWithoutSendingMail() {
        assertThatThrownBy(() -> userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "STAFF")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });

        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailVerificationRepository.count()).isZero();
    }

    @Test
    void loginSendsMailAndReturnsSuccessForValidUser() {
        var response = userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM"));

        assertThat(response.success()).isTrue();
        assertThat(response.verificationRequired()).isTrue();
        assertThat(response.expiresIn()).isEqualTo(180);
        verify(javaMailSender).send(any(MimeMessage.class));
        assertThat(emailVerificationRepository.count()).isOne();

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(savedUser.getLoginAt()).isNull();
        assertThat(savedUser.getRefreshTokenHash()).isNull();
    }

    @Test
    void loginDoesNotReturnSuccessWhenMailDeliveryFails() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getMessage()).isEqualTo("mail delivery failed");
                });

        verify(javaMailSender).send(any(MimeMessage.class));
        assertThat(emailVerificationRepository.count()).isZero();
    }

    @Test
    void verifyLoginFailsWithoutVerificationCodeRequest() {
        assertThatThrownBy(() -> userService.verifyLogin(new LoginVerifyRequest("pm001@example.com", "123456")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code not requested");
                });
    }

    @Test
    void verifyLoginFailsForWrongVerificationCode() {
        userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM"));

        assertThatThrownBy(() -> userService.verifyLogin(new LoginVerifyRequest("pm001@example.com", "000000")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code mismatch");
                });
    }

    @Test
    void verifyLoginIssuesTokensForCorrectVerificationCode() throws Exception {
        userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM"));
        String verificationCode = extractLatestVerificationCode();

        var response = userService.verifyLogin(new LoginVerifyRequest("pm001@example.com", verificationCode));

        assertThat(response.success()).isTrue();
        assertThat(response.employeeNumber()).isEqualTo("PM001");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void verifyLoginRejectsCodeIssuedForDifferentEmail() throws Exception {
        userRepository.save(createUser("ST001", "staff@example.com", "STAFF", "CorrectPassword1!"));
        userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM"));
        String verificationCode = extractLatestVerificationCode();

        assertThatThrownBy(() -> userService.verifyLogin(new LoginVerifyRequest("staff@example.com", verificationCode)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code not requested");
                });
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

    private User createUser(String employeeNumber, String email, String role, String rawPassword) {
        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName("Project Manager");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private String extractLatestVerificationCode() throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(captor.capture());

        Object content = captor.getValue().getContent();
        var matcher = Pattern.compile("\\b\\d{6}\\b").matcher(content.toString());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
