package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.LoginRequest;
import com.aivle26.aipm.Dto.LoginResendRequest;
import com.aivle26.aipm.Dto.LoginResponse;
import com.aivle26.aipm.Dto.LoginVerifyRequest;
import com.aivle26.aipm.Dto.LoginVerifyResponse;
import com.aivle26.aipm.Dto.PasswordChangeRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckResponse;
import com.aivle26.aipm.Dto.PasswordEmailSendRequest;
import com.aivle26.aipm.Dto.SignupRequest;
import com.aivle26.aipm.Dto.SignupResponse;
import com.aivle26.aipm.Entity.EmailVerification;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Entity.VerificationPurpose;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.EmailVerificationRepository;
import com.aivle26.aipm.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int LOGIN_VERIFICATION_TTL_SECONDS = 180;
    private static final int LOGIN_RESEND_WAIT_SECONDS = 60;
    private static final String INVALID_LOGIN_MESSAGE = "이메일, 비밀번호 또는 역할이 올바르지 않습니다.";
    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PASSWORD_POLICY_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");
    private static final int VERIFICATION_CODE_TTL_MINUTES = 5;
    private static final int VERIFICATION_RESEND_WAIT_SECONDS = 60;
    private static final int VERIFICATION_MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AuthService authService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String employeeNumber = request.employeeNumber().trim();
        String name = request.name().trim();
        String email = request.email().trim();
        String role = normalizeRole(request.role());

        validateSignupInput(email, request.password(), role);

        if (userRepository.existsById(employeeNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "employeeNumber already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email already exists");
        }

        User user = new User();
        user.setEmployeeNumber(employeeNumber);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setVerificationCodeFailedAttempts(0);

        User savedUser = userRepository.save(user);
        return new SignupResponse(
                savedUser.getEmployeeNumber(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getStatus()
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidLoginException);

        validateLoginCredentials(user, request.password(), request.role());
        issueLoginVerification(user, email);

        return new LoginResponse(
                true,
                true,
                "이메일로 인증번호가 발송되었습니다.",
                LOGIN_VERIFICATION_TTL_SECONDS
        );
    }

    @Transactional
    public LoginResponse resendLoginVerification(LoginResendRequest request) {
        String email = request.email().trim();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidLoginException);

        EmailVerification latestVerification = getLatestLoginVerification(email);
        validateLoginResendCooldown(latestVerification);
        issueLoginVerification(user, email);

        return new LoginResponse(
                true,
                true,
                "이메일로 인증번호가 발송되었습니다.",
                LOGIN_VERIFICATION_TTL_SECONDS
        );
    }

    @Transactional
    public LoginVerifyResponse verifyLogin(LoginVerifyRequest request) {
        String email = request.email().trim();
        EmailVerification verification = getLatestLoginVerification(email);

        if (verification.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code already used");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code expired");
        }

        if (!passwordEncoder.matches(request.verificationCode(), verification.getCodeHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code mismatch");
        }

        verification.setUsed(true);
        verification.setUsedAt(LocalDateTime.now());

        User user = getUserByEmployeeNumber(verification.getEmployeeNumber());
        var session = authService.issueSession(user);

        return new LoginVerifyResponse(
                true,
                "로그인에 성공했습니다.",
                user.getEmployeeNumber(),
                user.getName(),
                user.getRole(),
                session.accessToken(),
                session.refreshToken(),
                session.accessTokenExpiresAt(),
                session.absoluteExpiresAt(),
                session.lastActivityAt(),
                session.serverTime(),
                session.inactivityTimeoutMinutes()
        );
    }

    @Transactional
    public void sendPasswordEmailCode(PasswordEmailSendRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        String email = request.email().trim();
        LocalDateTime now = LocalDateTime.now();

        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email does not match registered email");
        }

        validateEmailDuplicate(user.getEmployeeNumber(), email);
        validateResendCooldown(user, email, now);

        String verificationCode = generateVerificationCode();
        clearVerificationState(user);
        user.setVerificationCode(verificationCode);
        user.setVerificationEmail(email);
        user.setVerificationCodeSentAt(now);
        user.setVerificationCodeExpiresAt(now.plusMinutes(VERIFICATION_CODE_TTL_MINUTES));
        user.setVerificationCodeFailedAttempts(0);
        user.setEmailVerified(false);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);

        mailService.sendVerificationCode(email, verificationCode);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PasswordEmailCheckResponse verifyPasswordEmailCode(PasswordEmailCheckRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        String email = request.email().trim();

        if (user.getVerificationCode() == null
                || user.getVerificationCodeExpiresAt() == null
                || user.getVerificationEmail() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code not requested");
        }

        if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            clearVerificationState(user);
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code expired");
        }

        if (!user.getVerificationEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email does not match verification request");
        }

        if (!request.code().equals(user.getVerificationCode())) {
            int failedAttempts = user.getVerificationCodeFailedAttempts() + 1;
            user.setVerificationCodeFailedAttempts(failedAttempts);
            if (failedAttempts >= VERIFICATION_MAX_FAILED_ATTEMPTS) {
                clearVerificationState(user);
                throw new ApiException(HttpStatus.BAD_REQUEST, "verification code invalidated");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code mismatch");
        }

        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email does not match registered email");
        }

        validateEmailDuplicate(user.getEmployeeNumber(), email);

        user.setEmail(email);
        user.setEmailVerified(true);
        clearVerificationState(user);

        String resetToken = generateUniqueResetToken();
        user.setResetToken(resetToken);
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES));

        return new PasswordEmailCheckResponse(resetToken, "email verification success");
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        validateResetToken(user, request.resetToken());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        clearVerificationState(user);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
    }

    private User getUserByEmployeeNumber(String employeeNumber) {
        return userRepository.findByEmployeeNumber(employeeNumber.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
    }

    private void validateLoginCredentials(User user, String rawPassword, String requestedRole) {
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw invalidLoginException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw invalidLoginException();
        }

        if (!normalizeRole(user.getRole()).equals(normalizeRole(requestedRole))) {
            throw invalidLoginException();
        }
    }

    private void validateSignupInput(String email, String rawPassword, String role) {
        if (!SIMPLE_EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        if (!PASSWORD_POLICY_PATTERN.matcher(rawPassword).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "password must be 8-72 chars and include letters and numbers");
        }
        if (!role.equals("PM") && !role.equals("STAFF")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "role must be PM or STAFF");
        }
    }

    private void issueLoginVerification(User user, String email) {
        String verificationCode = generateVerificationCode();
        LocalDateTime now = LocalDateTime.now();

        EmailVerification verification = new EmailVerification();
        verification.setEmployeeNumber(user.getEmployeeNumber());
        verification.setEmail(email);
        verification.setCodeHash(passwordEncoder.encode(verificationCode));
        verification.setPurpose(VerificationPurpose.LOGIN);
        verification.setExpiresAt(now.plusSeconds(LOGIN_VERIFICATION_TTL_SECONDS));
        verification.setUsed(false);
        verification.setCreatedAt(now);
        verification.setUsedAt(null);
        mailService.sendLoginVerificationCode(email, verificationCode);
        expireActiveLoginVerifications(email);
        emailVerificationRepository.save(verification);
    }

    private void expireActiveLoginVerifications(String email) {
        List<EmailVerification> activeVerifications = emailVerificationRepository
                .findByEmailIgnoreCaseAndPurposeAndUsedFalse(email, VerificationPurpose.LOGIN);
        LocalDateTime now = LocalDateTime.now();
        for (EmailVerification verification : activeVerifications) {
            if (verification.getExpiresAt().isAfter(now)) {
                verification.setExpiresAt(now);
            }
        }
    }

    private EmailVerification getLatestLoginVerification(String email) {
        return emailVerificationRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                        email,
                        VerificationPurpose.LOGIN
                )
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "verification code not requested"));
    }

    private void validateLoginResendCooldown(EmailVerification latestVerification) {
        LocalDateTime allowedAt = latestVerification.getCreatedAt().plusSeconds(LOGIN_RESEND_WAIT_SECONDS);
        if (allowedAt.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification code resend too soon");
        }
    }

    private void validateEmailDuplicate(String employeeNumber, String email) {
        if (userRepository.existsByEmailAndEmployeeNumberNot(email, employeeNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "email already exists");
        }
    }

    private void validateResetToken(User user, String resetToken) {
        if (user.getResetToken() == null || user.getResetTokenExpiresAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reset token not issued");
        }

        if (!user.getResetToken().equals(resetToken)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid reset token");
        }

        if (user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reset token expired");
        }
    }

    private String generateVerificationCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private void validateResendCooldown(User user, String email, LocalDateTime now) {
        if (user.getVerificationCodeSentAt() == null || user.getVerificationEmail() == null) {
            return;
        }
        if (!user.getVerificationEmail().equalsIgnoreCase(email)) {
            return;
        }
        if (user.getVerificationCodeSentAt().plusSeconds(VERIFICATION_RESEND_WAIT_SECONDS).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification code resend too soon");
        }
    }

    private void clearVerificationState(User user) {
        user.setVerificationCode(null);
        user.setVerificationEmail(null);
        user.setVerificationCodeSentAt(null);
        user.setVerificationCodeExpiresAt(null);
        user.setVerificationCodeFailedAttempts(0);
    }

    private String generateUniqueResetToken() {
        String token = UUID.randomUUID().toString();
        while (userRepository.existsByResetToken(token)) {
            token = UUID.randomUUID().toString();
        }
        return token;
    }

    private ApiException invalidLoginException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_MESSAGE);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
