package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AuthProperties;
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

    public JwtClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return toClaims(claims);
    }

    public JwtClaims parseAccessTokenAllowExpired(String token) {
        try {
            return parseAccessToken(token);
        } catch (ExpiredJwtException exception) {
            return toClaims(exception.getClaims());
        }
    }

    public boolean isInvalid(String token) {
        try {
            parseAccessToken(token);
            return false;
        } catch (JwtException | IllegalArgumentException exception) {
            return true;
        }
    }

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
