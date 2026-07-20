package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AuthProperties;
import com.aivle26.aipm.Dto.AuthSessionResponse;
import com.aivle26.aipm.Entity.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String EXPIRED_MESSAGE = "로그인 시간이 만료되었습니다. 다시 로그인해주세요.";
    private static final String INACTIVITY_MESSAGE = "장시간 사용하지 않아 로그아웃되었습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final JwtService jwtService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public AuthSessionResponse issueSession(User user) {
        LocalDateTime now = now();
        LocalDateTime absoluteExpiresAt = now.plusHours(authProperties.getAbsoluteLoginExpirationHours());
        LocalDateTime accessTokenExpiresAt = calculateAccessTokenExpiresAt(now, absoluteExpiresAt);
        String refreshToken = generateRefreshToken();

        user.setLoginAt(now);
        user.setAbsoluteExpiresAt(absoluteExpiresAt);
        user.setLastActivityAt(now);
        user.setRefreshTokenHash(passwordEncoder.encode(refreshToken));
        userRepository.save(user);

        return toSessionResponse(user, accessTokenExpiresAt, refreshToken);
    }

    @Transactional
    public AuthSessionResponse getCurrentSession(String employeeNumber) {
        User user = getUser(employeeNumber);
        validateActiveSession(user);
        validateInactivity(user);
        LocalDateTime accessTokenExpiresAt = calculateAccessTokenExpiresAt(now(), user.getAbsoluteExpiresAt());
        return toSessionResponse(user, accessTokenExpiresAt, null);
    }

    @Transactional
    public AuthSessionResponse refresh(String refreshToken) {
        User user = userRepository.findAll().stream()
                .filter(candidate -> candidate.getRefreshTokenHash() != null && passwordEncoder.matches(refreshToken, candidate.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(this::unauthorizedException);

        validateActiveSession(user);
        validateInactivity(user);

        LocalDateTime accessTokenExpiresAt = calculateAccessTokenExpiresAt(now(), user.getAbsoluteExpiresAt());
        return toSessionResponse(user, accessTokenExpiresAt, refreshToken);
    }

    @Transactional
    public void logout(String employeeNumber, String refreshToken) {
        User user = getUser(employeeNumber);
        if (refreshToken != null && user.getRefreshTokenHash() != null
                && !passwordEncoder.matches(refreshToken, user.getRefreshTokenHash())) {
            throw unauthorizedException();
        }
        clearSession(user);
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        userRepository.findAll().stream()
                .filter(candidate -> candidate.getRefreshTokenHash() != null
                        && passwordEncoder.matches(refreshToken, candidate.getRefreshTokenHash()))
                .findFirst()
                .ifPresent(this::clearSession);
    }

    @Transactional
    public void recordActivity(String employeeNumber) {
        User user = getUser(employeeNumber);
        validateActiveSession(user);
        validateInactivity(user);
        user.setLastActivityAt(now());
    }

    @Transactional
    public AuthenticatedUser authenticateAccessToken(String token) {
        JwtService.JwtClaims claims = parseAccessToken(token, false);
        User user = getUser(claims.subject());
        validateActiveSession(user);
        validateInactivity(user);
        return new AuthenticatedUser(user.getEmployeeNumber(), user.getRole());
    }

    @Transactional
    public void expireSession(String employeeNumber) {
        User user = getUser(employeeNumber);
        clearSession(user);
    }

    private JwtService.JwtClaims parseAccessToken(String token, boolean allowExpired) {
        try {
            JwtService.JwtClaims claims = allowExpired
                    ? jwtService.parseAccessTokenAllowExpired(token)
                    : jwtService.parseAccessToken(token);
            if (claims.absoluteExpiresAt().isBefore(now())) {
                throw tokenExpiredException();
            }
            return claims;
        } catch (io.jsonwebtoken.ExpiredJwtException exception) {
            throw tokenExpiredException();
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException exception) {
            throw unauthorizedException();
        }
    }

    private AuthSessionResponse toSessionResponse(User user, LocalDateTime accessTokenExpiresAt, String refreshToken) {
        String accessToken = jwtService.issueAccessToken(
                user.getEmployeeNumber(),
                user.getRole(),
                accessTokenExpiresAt,
                user.getAbsoluteExpiresAt()
        );
        return new AuthSessionResponse(
                true,
                user.getEmployeeNumber(),
                user.getName(),
                user.getRole(),
                accessToken,
                refreshToken,
                toEpochMillis(accessTokenExpiresAt),
                toEpochMillis(user.getAbsoluteExpiresAt()),
                toEpochMillis(user.getLastActivityAt()),
                toEpochMillis(now()),
                authProperties.getInactivityTimeoutMinutes()
        );
    }

    private LocalDateTime calculateAccessTokenExpiresAt(LocalDateTime baseTime, LocalDateTime absoluteExpiresAt) {
        LocalDateTime accessCandidate = baseTime.plusMinutes(authProperties.getAccessTokenExpirationMinutes());
        return accessCandidate.isBefore(absoluteExpiresAt) ? accessCandidate : absoluteExpiresAt;
    }

    private void validateActiveSession(User user) {
        if (user.getRefreshTokenHash() == null || user.getAbsoluteExpiresAt() == null || user.getLoginAt() == null) {
            throw unauthorizedException();
        }
        if (!user.getAbsoluteExpiresAt().isAfter(now())) {
            clearSession(user);
            throw tokenExpiredException();
        }
    }

    private void validateInactivity(User user) {
        if (user.getLastActivityAt() == null) {
            clearSession(user);
            throw inactivityTimeoutException();
        }
        LocalDateTime inactivityDeadline = user.getLastActivityAt().plusMinutes(authProperties.getInactivityTimeoutMinutes());
        if (!inactivityDeadline.isAfter(now())) {
            clearSession(user);
            throw inactivityTimeoutException();
        }
    }

    private void clearSession(User user) {
        user.setRefreshTokenHash(null);
        user.setLoginAt(null);
        user.setAbsoluteExpiresAt(null);
        user.setLastActivityAt(null);
    }

    private User getUser(String employeeNumber) {
        return userRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(this::unauthorizedException);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private long toEpochMillis(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private ApiException unauthorizedException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, AuthCodes.AUTH_UNAUTHORIZED, "인증이 필요합니다.");
    }

    private ApiException tokenExpiredException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, AuthCodes.AUTH_TOKEN_EXPIRED, EXPIRED_MESSAGE);
    }

    private ApiException inactivityTimeoutException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, AuthCodes.AUTH_INACTIVITY_TIMEOUT, INACTIVITY_MESSAGE);
    }
}
