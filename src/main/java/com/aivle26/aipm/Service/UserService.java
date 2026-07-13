package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.LoginRequest;
import com.aivle26.aipm.Dto.LoginResponse;
import com.aivle26.aipm.Dto.PasswordChangeRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckResponse;
import com.aivle26.aipm.Dto.PasswordEmailSendRequest;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Entity.UserStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "inactive account");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid employeeNumber or password");
        }

        String message = user.getStatus() == UserStatus.MUST_CHANGE_PASSWORD
                ? "email verification and password change required"
                : "login success";

        return new LoginResponse(
                user.getEmployeeNumber(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                message
        );
    }

    @Transactional
    public void sendPasswordEmailCode(PasswordEmailSendRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        String email = request.email().trim();

        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email does not match registered email");
        }

        validateEmailDuplicate(user.getEmployeeNumber(), email);

        String verificationCode = generateVerificationCode();
        user.setVerificationCode(verificationCode);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
        user.setEmailVerified(false);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);

        mailService.sendVerificationCode(email, verificationCode);
    }

    @Transactional
    public PasswordEmailCheckResponse verifyPasswordEmailCode(PasswordEmailCheckRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        String email = request.email().trim();

        if (user.getVerificationCode() == null || user.getVerificationCodeExpiresAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code not requested");
        }

        if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code expired");
        }

        if (!request.code().equals(user.getVerificationCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "verification code mismatch");
        }

        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "email does not match registered email");
        }

        validateEmailDuplicate(user.getEmployeeNumber(), email);

        user.setEmail(email);
        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);

        String resetToken = generateUniqueResetToken();
        user.setResetToken(resetToken);
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(5));

        return new PasswordEmailCheckResponse(resetToken, "email verification success");
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        User user = getUserByEmployeeNumber(request.employeeNumber());
        validateResetToken(user, request.resetToken());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
    }

    private User getUserByEmployeeNumber(String employeeNumber) {
        return userRepository.findByEmployeeNumber(employeeNumber.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
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

    private String generateUniqueResetToken() {
        String token = UUID.randomUUID().toString();
        while (userRepository.existsByResetToken(token)) {
            token = UUID.randomUUID().toString();
        }
        return token;
    }
}
