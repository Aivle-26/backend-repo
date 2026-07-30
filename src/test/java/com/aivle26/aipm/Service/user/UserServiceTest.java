package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.auth.LoginRequest;
import com.aivle26.aipm.Dto.auth.LoginVerifyRequest;
import com.aivle26.aipm.Dto.auth.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.auth.PasswordEmailSendRequest;
import com.aivle26.aipm.Dto.auth.SignupRequest;
import com.aivle26.aipm.Dto.auth.SignupVerifyRequest;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.user.EmailVerification;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Entity.user.VerificationPurpose;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.user.EmailVerificationRepository;
import com.aivle26.aipm.Repository.user.UserRepository;

import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void signupSendsVerificationWithoutCreatingUser() throws Exception {
        var response = userService.signup(new SignupRequest(
                "PM002",
                "New PM",
                "newpm@example.com",
                "Signup123",
                "PM"
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.verificationRequired()).isTrue();
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(userRepository.findById("PM002")).isEmpty();
        assertThat(emailVerificationRepository.count()).isOne();
        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("[PM Agent] 회원가입 이메일 인증");
        assertThat(captor.getValue().getHeader("Auto-Submitted", null)).isEqualTo("auto-generated");
    }

    @Test
    void verifySignupCreatesPmUser() throws Exception {
        userService.signup(new SignupRequest(
                "PM002",
                "New PM",
                "newpm@example.com",
                "Signup123",
                "PM"
        ));
        String verificationCode = extractLatestVerificationCode();

        var response = userService.verifySignup(new SignupVerifyRequest("newpm@example.com", verificationCode));

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
    void verifySignupCreatesStaffUser() throws Exception {
        userService.signup(new SignupRequest(
                "ST002",
                "New Staff",
                "newstaff@example.com",
                "Signup123",
                "staff"
        ));
        String verificationCode = extractLatestVerificationCode();

        var response = userService.verifySignup(new SignupVerifyRequest("newstaff@example.com", verificationCode));
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
        verify(javaMailSender, never()).send(any(MimeMessage.class));
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
    void signupDoesNotCreatePendingVerificationWhenMailDeliveryFails() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> userService.signup(new SignupRequest(
                "PM004",
                "Mail Fail",
                "mailfail@example.com",
                "Signup123",
                "PM"
        )))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getMessage()).isEqualTo("mail delivery failed");
                });

        assertThat(userRepository.findById("PM004")).isEmpty();
        assertThat(emailVerificationRepository.count()).isZero();
        verify(javaMailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void signupRetriesTransientMailFailure() {
        doThrow(new MailSendException("temporary smtp failure"))
                .doNothing()
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        var response = userService.signup(new SignupRequest(
                "PM005",
                "Retry Mail",
                "retry@example.com",
                "Signup123",
                "PM"
        ));

        assertThat(response.success()).isTrue();
        assertThat(emailVerificationRepository.count()).isOne();
        verify(javaMailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void verifySignupFailsWithoutVerificationCodeRequest() {
        assertThatThrownBy(() -> userService.verifySignup(new SignupVerifyRequest("newpm@example.com", "123456")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code not requested");
                });
    }

    @Test
    void verifySignupFailsForWrongCodeWithoutCreatingUser() {
        userService.signup(new SignupRequest(
                "PM004",
                "Wrong Code",
                "wrongcode@example.com",
                "Signup123",
                "PM"
        ));

        assertThatThrownBy(() -> userService.verifySignup(new SignupVerifyRequest("wrongcode@example.com", "000000")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code mismatch");
                });

        assertThat(userRepository.findById("PM004")).isEmpty();
    }

    @Test
    void verifySignupInvalidatesCodeAfterFiveFailures() {
        userService.signup(new SignupRequest(
                "PM006",
                "Attempt Limited",
                "attempts@example.com",
                "Signup123",
                "PM"
        ));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> userService.verifySignup(
                    new SignupVerifyRequest("attempts@example.com", "000000")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("verification code mismatch");
        }

        assertThatThrownBy(() -> userService.verifySignup(
                new SignupVerifyRequest("attempts@example.com", "000000")))
                .isInstanceOf(ApiException.class)
                .hasMessage("verification code invalidated");

        EmailVerification verification = emailVerificationRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                        "attempts@example.com",
                        VerificationPurpose.SIGNUP
                )
                .orElseThrow();
        assertThat(verification.isUsed()).isTrue();
        assertThat(verification.getFailedAttempts()).isEqualTo(5);
    }

    @Test
    void verifySignupRejectsCodeIssuedForDifferentEmail() throws Exception {
        userService.signup(new SignupRequest(
                "PM004",
                "Different Email",
                "signup-a@example.com",
                "Signup123",
                "PM"
        ));
        String verificationCode = extractLatestVerificationCode();

        assertThatThrownBy(() -> userService.verifySignup(new SignupVerifyRequest("signup-b@example.com", verificationCode)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code not requested");
                });

        assertThat(userRepository.findById("PM004")).isEmpty();
    }

    @Test
    void signupUserCanLoginDirectly() throws Exception {
        userService.signup(new SignupRequest(
                "PM003",
                "Login PM",
                "loginpm@example.com",
                "Signup123",
                "PM"
        ));
        String signupCode = extractLatestVerificationCode();
        userService.verifySignup(new SignupVerifyRequest("loginpm@example.com", signupCode));

        var loginResponse = userService.login(new LoginRequest("loginpm@example.com", "Signup123", "PM"));

        assertThat(loginResponse.success()).isTrue();
        assertThat(loginResponse.employeeNumber()).isEqualTo("PM003");
        assertThat(loginResponse.accessToken()).isNotBlank();
    }

    @Test
    void signupUserLoginFailsForWrongRole() throws Exception {
        userService.signup(new SignupRequest(
                "ST003",
                "Login Staff",
                "loginstaff@example.com",
                "Signup123",
                "STAFF"
        ));
        String signupCode = extractLatestVerificationCode();
        userService.verifySignup(new SignupVerifyRequest("loginstaff@example.com", signupCode));
        clearInvocations(javaMailSender);

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
    void loginIssuesTokensWithoutSendingMailForValidUser() {
        var response = userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM"));

        assertThat(response.success()).isTrue();
        assertThat(response.employeeNumber()).isEqualTo("PM001");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailVerificationRepository.count()).isZero();

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(savedUser.getLoginAt()).isNotNull();
        assertThat(savedUser.getRefreshTokenHash()).isNotBlank();
    }

    @Test
    void loginDoesNotDependOnMailDelivery() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));

        var response = userService.login(new LoginRequest("pm001@example.com", "CorrectPassword1!", "PM"));

        assertThat(response.accessToken()).isNotBlank();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
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
        saveLoginVerification("PM001", "pm001@example.com", "123456");

        assertThatThrownBy(() -> userService.verifyLogin(new LoginVerifyRequest("pm001@example.com", "000000")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code mismatch");
                });
    }

    @Test
    void verifyLoginIssuesTokensForCorrectVerificationCode() throws Exception {
        String verificationCode = "123456";
        saveLoginVerification("PM001", "pm001@example.com", verificationCode);

        var response = userService.verifyLogin(new LoginVerifyRequest("pm001@example.com", verificationCode));

        assertThat(response.success()).isTrue();
        assertThat(response.employeeNumber()).isEqualTo("PM001");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void verifyLoginRejectsCodeIssuedForDifferentEmail() throws Exception {
        userRepository.save(createUser("ST001", "staff@example.com", "STAFF", "CorrectPassword1!"));
        String verificationCode = "123456";
        saveLoginVerification("PM001", "pm001@example.com", verificationCode);

        assertThatThrownBy(() -> userService.verifyLogin(new LoginVerifyRequest("staff@example.com", verificationCode)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("verification code not requested");
                });
    }

    @Test
    void sendPasswordEmailCodeSuccess() throws Exception {
        userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com"));
        String verificationCode = extractLatestVerificationCode();

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(savedUser.getVerificationEmail()).isEqualTo("pm001@example.com");
        assertThat(savedUser.getVerificationCode()).isNotEqualTo(verificationCode);
        assertThat(passwordEncoder.matches(verificationCode, savedUser.getVerificationCode())).isTrue();
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
    void sendPasswordEmailCodeRejectsAccountWithoutRegisteredEmail() {
        userRepository.save(createUser("PM007", null, "PM", "CorrectPassword1!"));

        assertThatThrownBy(() -> userService.sendPasswordEmailCode(
                new PasswordEmailSendRequest("PM007", "attacker@example.com")))
                .isInstanceOf(ApiException.class)
                .hasMessage("email does not match registered email");

        verify(javaMailSender, never()).send(any(MimeMessage.class));
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
    void verifyPasswordEmailCodeSuccessClearsVerificationCode() throws Exception {
        userService.sendPasswordEmailCode(new PasswordEmailSendRequest("PM001", "pm001@example.com"));
        String verificationCode = extractLatestVerificationCode();

        var response = userService.verifyPasswordEmailCode(
                new PasswordEmailCheckRequest("PM001", "pm001@example.com", verificationCode)
        );

        User savedUser = userRepository.findById("PM001").orElseThrow();
        assertThat(response.resetToken()).isNotEqualTo(savedUser.getResetToken());
        assertThat(passwordEncoder.matches(response.resetToken(), savedUser.getResetToken())).isTrue();
        assertThat(savedUser.getEmail()).isEqualTo("pm001@example.com");
        assertThat(savedUser.getVerificationCode()).isNull();
        assertThat(savedUser.getVerificationEmail()).isNull();
        assertThat(savedUser.getVerificationCodeExpiresAt()).isNull();
    }

    private void saveLoginVerification(String employeeNumber, String email, String rawCode) {
        EmailVerification verification = new EmailVerification();
        verification.setEmployeeNumber(employeeNumber);
        verification.setEmail(email);
        verification.setCodeHash(passwordEncoder.encode(rawCode));
        verification.setPurpose(VerificationPurpose.LOGIN);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(3));
        verification.setUsed(false);
        verification.setFailedAttempts(0);
        verification.setCreatedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);
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

        var matcher = Pattern.compile("\\b\\d{6}\\b").matcher(readMessageText(captor.getValue()));
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private String readMessageText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                result.append(readMessageText(multipart.getBodyPart(index)));
            }
            return result.toString();
        }
        return "";
    }
}
