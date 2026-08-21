package com.aivle26.aipm.Service.auth;

import com.aivle26.aipm.Config.auth.AuthProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ABSOLUTE_EXP = "absoluteExp";

    private final SecretKey signingKey;
    private final Clock clock;

    public JwtService(AuthProperties authProperties) {
        this.signingKey = Keys.hmacShaKeyFor(authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.clock = Clock.systemUTC();
    }

    // 사번·역할·만료 시각을 JWT 클레임에 담아 서명된 액세스 토큰으로 반환한다.
    public String issueAccessToken(String employeeNumber, String role, LocalDateTime expiresAt, LocalDateTime absoluteExpiresAt) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(employeeNumber)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt.toInstant(ZoneOffset.UTC)))
                .claims(Map.of(
                        CLAIM_ROLE, role,
                        CLAIM_ABSOLUTE_EXP, absoluteExpiresAt.toInstant(ZoneOffset.UTC).toEpochMilli()
                ))
                .signWith(signingKey)
                .compact();
    }

    // 액세스 토큰의 서명과 만료를 검증해 인증 클레임으로 반환한다.
    public JwtClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return toClaims(claims);
    }

    // 만료된 토큰도 서명은 검증해 세션 정리에 사용할 인증 클레임을 반환한다.
    public JwtClaims parseAccessTokenAllowExpired(String token) {
        try {
            return parseAccessToken(token);
        } catch (ExpiredJwtException exception) {
            return toClaims(exception.getClaims());
        }
    }

    // 토큰 파싱 결과를 기준으로 액세스 토큰의 유효 여부를 boolean으로 반환한다.
    public boolean isInvalid(String token) {
        try {
            parseAccessToken(token);
            return false;
        } catch (JwtException | IllegalArgumentException exception) {
            return true;
        }
    }

    // JWT 원시 클레임을 사번·역할·만료값이 포함된 내부 인증 레코드로 변환한다.
    private JwtClaims toClaims(Claims claims) {
        Long absoluteExp = claims.get(CLAIM_ABSOLUTE_EXP, Long.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        return new JwtClaims(
                claims.getSubject(),
                role,
                claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(absoluteExp), ZoneOffset.UTC)
        );
    }

    public record JwtClaims(
            String subject,
            String role,
            LocalDateTime expiresAt,
            LocalDateTime absoluteExpiresAt
    ) {
    }
}
