package com.aivle26.aipm.Service.auth;

import com.aivle26.aipm.Config.auth.AuthProperties;
import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Entity.user.User;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.user.UserRepository;

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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final JwtService jwtService;
    private final Clock clock = Clock.systemUTC();

    // 사용자에게 액세스·리프레시 토큰과 세션 만료값을 발급해 인증 응답으로 반환한다.
    @Transactional
    public AuthSessionResponse issueSession(User user) {
        LocalDateTime now = now();
        LocalDateTime absoluteExpiresAt = now.plusHours(authProperties.getAbsoluteLoginExpirationHours());
        LocalDateTime accessTokenExpiresAt = calculateAccessTokenExpiresAt(now, absoluteExpiresAt);
        String refreshToken = generateRefreshToken();

        user.setLoginAt(now);
        user.setAbsoluteExpiresAt(absoluteExpiresAt);
        user.setRefreshTokenHash(passwordEncoder.encode(refreshToken));
        userRepository.save(user);

        return toSessionResponse(user, accessTokenExpiresAt, refreshToken);
    }

    // 사번의 활성 세션과 비활동 시간을 검증해 현재 인증 세션을 반환한다.
    @Transactional
    public AuthSessionResponse getCurrentSession(String employeeNumber) {
        User user = getUser(employeeNumber);
        validateActiveSession(user);
        LocalDateTime accessTokenExpiresAt = calculateAccessTokenExpiresAt(now(), user.getAbsoluteExpiresAt());
        return toSessionResponse(user, accessTokenExpiresAt, null);
    }

    // 리프레시 토큰의 사용자와 세션을 검증해 액세스 토큰을 갱신한다.
    @Transactional
    public AuthSessionResponse refresh(String refreshToken) {
        User user = userRepository.findAll().stream()
                .filter(candidate -> candidate.getRefreshTokenHash() != null && passwordEncoder.matches(refreshToken, candidate.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(this::unauthorizedException);

        validateActiveSession(user);

        LocalDateTime accessTokenExpiresAt = calculateAccessTokenExpiresAt(now(), user.getAbsoluteExpiresAt());
        return toSessionResponse(user, accessTokenExpiresAt, refreshToken);
    }

    // 사번과 선택 리프레시 토큰을 확인해 해당 사용자의 인증 세션을 종료한다.
    @Transactional
    public void logout(String employeeNumber, String refreshToken) {
        User user = getUser(employeeNumber);
        if (refreshToken != null && user.getRefreshTokenHash() != null
                && !passwordEncoder.matches(refreshToken, user.getRefreshTokenHash())) {
            throw unauthorizedException();
        }
        clearSession(user);
    }

    // 리프레시 토큰으로 사용자를 찾아 일치하는 인증 세션을 종료한다.
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

    // 액세스 토큰과 서버 세션을 검증해 Spring Security용 인증 사용자를 반환한다.
    @Transactional
    public AuthenticatedUser authenticateAccessToken(String token) {
        JwtService.JwtClaims claims = parseAccessToken(token, false);
        User user = getUser(claims.subject());
        validateActiveSession(user);
        return new AuthenticatedUser(user.getEmployeeNumber(), user.getRole());
    }

    // 사번으로 사용자를 조회해 저장된 인증 세션 정보를 만료 처리한다.
    @Transactional
    public void expireSession(String employeeNumber) {
        User user = getUser(employeeNumber);
        clearSession(user);
    }

    // 만료 허용 여부에 따라 액세스 토큰을 해석하고 인증 예외를 통일해 반환한다.
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

    // 사용자와 토큰 만료 정보를 클라이언트용 인증 세션 DTO로 조립한다.
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
                toEpochMillis(now())
        );
    }

    // 기준 시각의 토큰 수명과 세션 절대 만료 중 더 이른 시각을 반환한다.
    private LocalDateTime calculateAccessTokenExpiresAt(LocalDateTime baseTime, LocalDateTime absoluteExpiresAt) {
        LocalDateTime accessCandidate = baseTime.plusMinutes(authProperties.getAccessTokenExpirationMinutes());
        return accessCandidate.isBefore(absoluteExpiresAt) ? accessCandidate : absoluteExpiresAt;
    }

    // 사용자의 리프레시 토큰과 절대 만료 시각이 유효한 활성 세션인지 검증한다.
    private void validateActiveSession(User user) {
        if (user.getRefreshTokenHash() == null || user.getAbsoluteExpiresAt() == null || user.getLoginAt() == null) {
            throw unauthorizedException();
        }
        if (!user.getAbsoluteExpiresAt().isAfter(now())) {
            clearSession(user);
            throw tokenExpiredException();
        }
    }

    // 사용자에 저장된 리프레시 토큰과 세션·활동 만료 정보를 초기화한다.
    private void clearSession(User user) {
        user.setRefreshTokenHash(null);
        user.setLoginAt(null);
        user.setAbsoluteExpiresAt(null);
    }

    // 사번으로 인증 사용자를 조회하고 없으면 권한 없음 예외를 발생시킨다.
    private User getUser(String employeeNumber) {
        return userRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(this::unauthorizedException);
    }

    // 보안 난수 기반 URL-safe 리프레시 토큰을 생성해 반환한다.
    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // UTC 기준 LocalDateTime을 클라이언트 전달용 epoch 밀리초로 변환한다.
    private long toEpochMillis(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    // 주입된 UTC 시계를 기준으로 현재 LocalDateTime을 반환한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // 인증 정보가 유효하지 않을 때 사용할 공통 권한 없음 예외를 생성한다.
    private ApiException unauthorizedException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, AuthCodes.AUTH_UNAUTHORIZED, "인증이 필요합니다.");
    }

    // 액세스 또는 리프레시 토큰 만료를 나타내는 공통 예외를 생성한다.
    private ApiException tokenExpiredException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, AuthCodes.AUTH_TOKEN_EXPIRED, EXPIRED_MESSAGE);
    }

}
