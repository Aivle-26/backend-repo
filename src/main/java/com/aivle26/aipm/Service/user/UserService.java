package com.aivle26.aipm.Service.user;

import com.aivle26.aipm.Dto.auth.LoginRequest;
import com.aivle26.aipm.Dto.auth.LoginResendRequest;
import com.aivle26.aipm.Dto.auth.LoginResponse;
import com.aivle26.aipm.Dto.auth.LoginVerifyRequest;
import com.aivle26.aipm.Dto.auth.LoginVerifyResponse;
import com.aivle26.aipm.Dto.auth.PasswordChangeRequest;
import com.aivle26.aipm.Dto.auth.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.auth.PasswordEmailCheckResponse;
import com.aivle26.aipm.Dto.auth.PasswordEmailSendRequest;
import com.aivle26.aipm.Dto.auth.SignupRequest;
import com.aivle26.aipm.Dto.auth.SignupResponse;
import com.aivle26.aipm.Dto.auth.SignupStartResponse;
import com.aivle26.aipm.Dto.auth.SignupVerifyRequest;
import com.aivle26.aipm.Entity.user.EmailVerification;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Entity.user.UserStatus;
import com.aivle26.aipm.Entity.user.VerificationPurpose;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.user.EmailVerificationRepository;
import com.aivle26.aipm.Repository.user.UserRepository;
import com.aivle26.aipm.Service.auth.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int LOGIN_VERIFICATION_TTL_SECONDS = 180;
    private static final int LOGIN_RESEND_WAIT_SECONDS = 60;
    private static final int SIGNUP_VERIFICATION_TTL_SECONDS = 300;
    private static final int SIGNUP_RESEND_WAIT_SECONDS = 60;
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

    // 가입 입력과 중복 여부를 검증해 이메일 인증 레코드를 발급하고 시작 응답을 반환한다.
    @Transactional
    public SignupStartResponse signup(SignupRequest request) {
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

        EmailVerification latestByEmail = emailVerificationRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, VerificationPurpose.SIGNUP)
                .orElse(null);
        EmailVerification latestByEmployeeNumber = emailVerificationRepository
                .findTopByEmployeeNumberAndPurposeOrderByCreatedAtDesc(employeeNumber, VerificationPurpose.SIGNUP)
                .orElse(null);
        validateSignupResendCooldown(latestByEmail, latestByEmployeeNumber);
        issueSignupVerification(employeeNumber, name, email, request.password(), role);

        return new SignupStartResponse(
                true,
                true,
                "이메일로 회원가입 인증번호가 발송되었습니다.",
                SIGNUP_VERIFICATION_TTL_SECONDS
        );
    }

    // 가입 인증 코드를 검증해 신규 사용자를 저장하고 가입 완료 응답을 반환한다.
    @Transactional(noRollbackFor = ApiException.class)
    public SignupResponse verifySignup(SignupVerifyRequest request) {
        String email = request.email().trim();
        EmailVerification verification = getLatestSignupVerification(email);

        if (verification.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code already used");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code expired");
        }

        validateVerificationCode(verification, request.verificationCode());

        if (verification.getSignupName() == null
                || verification.getSignupPasswordHash() == null
                || verification.getSignupRole() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "signup request not found");
        }

        if (userRepository.existsById(verification.getEmployeeNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "employeeNumber already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email already exists");
        }

        User user = new User();
        user.setEmployeeNumber(verification.getEmployeeNumber());
        user.setName(verification.getSignupName());
        user.setEmail(email);
        user.setPassword(verification.getSignupPasswordHash());
        user.setRole(verification.getSignupRole());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setVerificationCodeFailedAttempts(0);

        User savedUser = userRepository.save(user);
        verification.setUsed(true);
        verification.setUsedAt(LocalDateTime.now());
        expireActiveSignupVerifications(verification.getEmployeeNumber(), email);

        return new SignupResponse(
                savedUser.getEmployeeNumber(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getStatus()
        );
    }

    // 이메일·비밀번호·역할을 검증해 Access·Refresh Token이 포함된 세션을 즉시 반환한다.
    @Transactional
    public LoginVerifyResponse login(LoginRequest request) {
        String email = request.email().trim();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidLoginException);

        validateLoginCredentials(user, request.password(), request.role());
        var session = authService.issueSession(user);

        return new LoginVerifyResponse(
                true,
                "login success",
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

    // 로그인 재전송 제한을 확인해 새 인증 코드를 발급하고 전송 결과를 반환한다.
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

    // 로그인 인증 코드를 검증해 사용자를 활성화하고 새 인증 세션을 반환한다.
    @Transactional(noRollbackFor = ApiException.class)
    public LoginVerifyResponse verifyLogin(LoginVerifyRequest request) {
        String email = request.email().trim();
        EmailVerification verification = getLatestLoginVerification(email);

        if (verification.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code already used");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code expired");
        }

        validateVerificationCode(verification, request.verificationCode());

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

    // 사용자 이메일을 검증해 비밀번호 재설정 인증 코드를 발급하고 전송한다.
    @Transactional
    public void sendPasswordEmailCode(PasswordEmailSendRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        String email = request.email().trim();
        LocalDateTime now = LocalDateTime.now();

        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email does not match registered email");
        }

        validateEmailDuplicate(user.getEmployeeNumber(), email);
        validateResendCooldown(user, email, now);

        String verificationCode = generateVerificationCode();
        clearVerificationState(user);
        user.setVerificationCode(passwordEncoder.encode(verificationCode));
        user.setVerificationEmail(email);
        user.setVerificationCodeSentAt(now);
        user.setVerificationCodeExpiresAt(now.plusMinutes(VERIFICATION_CODE_TTL_MINUTES));
        user.setVerificationCodeFailedAttempts(0);
        user.setEmailVerified(false);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);

        userRepository.saveAndFlush(user);
        mailService.sendPasswordResetVerificationCode(email, verificationCode);
    }

    // 비밀번호 인증 코드와 시도 횟수를 검증해 일회성 재설정 토큰을 반환한다.
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

        if (!passwordEncoder.matches(request.code(), user.getVerificationCode())) {
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

        user.setEmailVerified(true);
        clearVerificationState(user);

        String resetToken = generateResetToken();
        user.setResetToken(passwordEncoder.encode(resetToken));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES));

        return new PasswordEmailCheckResponse(resetToken, "email verification success");
    }

    // 재설정 토큰과 새 비밀번호 정책을 검증해 암호를 변경하고 인증 상태를 초기화한다.
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

    // 사번으로 사용자를 조회하고 존재하지 않으면 기존 사용자 없음 예외를 발생시킨다.
    private User getUserByEmployeeNumber(String employeeNumber) {
        return userRepository.findByEmployeeNumber(employeeNumber.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
    }

    // 사용자 상태와 입력 비밀번호·역할이 로그인 계정 정보와 일치하는지 검증한다.
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

    // 가입 이메일 형식, 비밀번호 정책, 허용 역할을 검증한다.
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

    // 기존 로그인 인증을 만료시키고 새 인증 코드 레코드를 저장·메일 전송한다.
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
        verification.setFailedAttempts(0);
        verification.setCreatedAt(now);
        verification.setUsedAt(null);
        expireActiveLoginVerifications(email);
        emailVerificationRepository.saveAndFlush(verification);
        mailService.sendLoginVerificationCode(email, verificationCode);
    }

    // 가입 정보를 보관한 새 인증 레코드를 생성하고 이메일 코드를 전송한다.
    private void issueSignupVerification(String employeeNumber, String name, String email, String rawPassword, String role) {
        String verificationCode = generateVerificationCode();
        LocalDateTime now = LocalDateTime.now();

        EmailVerification verification = new EmailVerification();
        verification.setEmployeeNumber(employeeNumber);
        verification.setEmail(email);
        verification.setCodeHash(passwordEncoder.encode(verificationCode));
        verification.setSignupName(name);
        verification.setSignupPasswordHash(passwordEncoder.encode(rawPassword));
        verification.setSignupRole(role);
        verification.setPurpose(VerificationPurpose.SIGNUP);
        verification.setExpiresAt(now.plusSeconds(SIGNUP_VERIFICATION_TTL_SECONDS));
        verification.setUsed(false);
        verification.setFailedAttempts(0);
        verification.setCreatedAt(now);
        verification.setUsedAt(null);
        expireActiveSignupVerifications(employeeNumber, email);
        emailVerificationRepository.saveAndFlush(verification);
        mailService.sendSignupVerificationCode(email, verificationCode);
    }

    // 이메일에 남아 있는 미사용 로그인 인증 레코드를 모두 만료 처리한다.
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

    // 사번 또는 이메일에 남아 있는 미사용 가입 인증 레코드를 모두 만료 처리한다.
    private void expireActiveSignupVerifications(String employeeNumber, String email) {
        LocalDateTime now = LocalDateTime.now();
        for (EmailVerification verification : emailVerificationRepository
                .findByEmailIgnoreCaseAndPurposeAndUsedFalse(email, VerificationPurpose.SIGNUP)) {
            if (verification.getExpiresAt().isAfter(now)) {
                verification.setExpiresAt(now);
            }
        }
        for (EmailVerification verification : emailVerificationRepository
                .findByEmployeeNumberAndPurposeAndUsedFalse(employeeNumber, VerificationPurpose.SIGNUP)) {
            if (verification.getExpiresAt().isAfter(now)) {
                verification.setExpiresAt(now);
            }
        }
    }

    // 이메일의 가장 최근 로그인 인증 레코드를 조회해 반환한다.
    private EmailVerification getLatestLoginVerification(String email) {
        return emailVerificationRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                        email,
                        VerificationPurpose.LOGIN
                )
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "verification code not requested"));
    }

    // 이메일의 가장 최근 가입 인증 레코드를 조회해 반환한다.
    private EmailVerification getLatestSignupVerification(String email) {
        return emailVerificationRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                        email,
                        VerificationPurpose.SIGNUP
                )
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "verification code not requested"));
    }

    // 최근 로그인 인증 발급 시각을 기준으로 재전송 대기 시간이 지났는지 검증한다.
    private void validateLoginResendCooldown(EmailVerification latestVerification) {
        LocalDateTime allowedAt = latestVerification.getCreatedAt().plusSeconds(LOGIN_RESEND_WAIT_SECONDS);
        if (allowedAt.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification code resend too soon");
        }
    }

    // 이메일과 사번의 최근 가입 인증을 비교해 재전송 대기 시간이 지났는지 검증한다.
    private void validateSignupResendCooldown(EmailVerification latestByEmail, EmailVerification latestByEmployeeNumber) {
        LocalDateTime now = LocalDateTime.now();
        if (latestByEmail != null
                && !latestByEmail.isUsed()
                && latestByEmail.getCreatedAt().plusSeconds(SIGNUP_RESEND_WAIT_SECONDS).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification code resend too soon");
        }
        if (latestByEmployeeNumber != null
                && !latestByEmployeeNumber.isUsed()
                && latestByEmployeeNumber.getCreatedAt().plusSeconds(SIGNUP_RESEND_WAIT_SECONDS).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification code resend too soon");
        }
    }

    // 사번과 이메일이 기존 사용자 계정에 중복 등록되지 않았는지 검증한다.
    private void validateEmailDuplicate(String employeeNumber, String email) {
        if (userRepository.existsByEmailAndEmployeeNumberNot(email, employeeNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "email already exists");
        }
    }

    // 사용자의 비밀번호 재설정 토큰 일치 여부와 만료 시각을 검증한다.
    private void validateResetToken(User user, String resetToken) {
        if (user.getResetToken() == null || user.getResetTokenExpiresAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reset token not issued");
        }

        if (!passwordEncoder.matches(resetToken, user.getResetToken())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid reset token");
        }

        if (user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reset token expired");
        }
    }

    // 보안 난수로 고정 길이 이메일 인증 코드를 생성해 반환한다.
    private String generateVerificationCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    // 비밀번호 인증 코드의 최근 발급 이력으로 재전송 제한을 검증한다.
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

    // 사용자에게 저장된 인증 코드·만료·시도 횟수·재설정 토큰 상태를 초기화한다.
    private void clearVerificationState(User user) {
        user.setVerificationCode(null);
        user.setVerificationEmail(null);
        user.setVerificationCodeSentAt(null);
        user.setVerificationCodeExpiresAt(null);
        user.setVerificationCodeFailedAttempts(0);
    }

    private void validateVerificationCode(EmailVerification verification, String rawCode) {
        if (passwordEncoder.matches(rawCode, verification.getCodeHash())) {
            return;
        }
        int failedAttempts = verification.getFailedAttempts() + 1;
        verification.setFailedAttempts(failedAttempts);
        if (failedAttempts >= VERIFICATION_MAX_FAILED_ATTEMPTS) {
            verification.setUsed(true);
            verification.setUsedAt(LocalDateTime.now());
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code invalidated");
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "verification code mismatch");
    }

    // 보안 난수 기반 일회성 재설정 토큰을 생성한다.
    private String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 계정 존재 여부를 노출하지 않는 공통 로그인 실패 예외를 생성한다.
    private ApiException invalidLoginException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_MESSAGE);
    }

    // 입력 역할의 공백을 제거하고 대문자로 정규화해 반환한다.
    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
