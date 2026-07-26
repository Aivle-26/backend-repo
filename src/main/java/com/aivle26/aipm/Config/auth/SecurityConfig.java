package com.aivle26.aipm.Config.auth;

import com.aivle26.aipm.Service.auth.AuthCodes;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    // 공개·인증 API와 JWT 필터 및 인증 오류 응답 정책을 적용한 보안 체인을 생성한다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/users/signup",
                                "/api/users/signup/verify",
                                "/api/users/login",
                                "/api/users/login/verify",
                                "/api/users/login/resend",
                                "/api/users/refresh",
                                "/api/users/logout",
                                "/api/users/password/**",
                                "/static/**",
                                "/test-auth.html"
                        ).permitAll()
                        .requestMatchers("/api/projects/**", "/api/admin/**", "/api/users/me", "/api/users/session", "/api/users/activity")
                        .authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) ->
                                writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, AuthCodes.AUTH_UNAUTHORIZED, "Unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeAuthError(response, HttpServletResponse.SC_FORBIDDEN, "AUTH_FORBIDDEN", "Forbidden", "Access is denied"))
                )
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 사용자 비밀번호 저장과 검증에 사용할 BCrypt 인코더를 생성한다.
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 설정된 출처와 자격 증명 정책을 적용한 전역 CORS 구성을 반환한다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // 인증 실패 정보를 UTF-8 JSON 형식으로 HTTP 응답에 기록한다.
    private void writeAuthError(HttpServletResponse response, int status, String code, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        PrintWriter writer = response.getWriter();
        writer.write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":" + status
                + ",\"code\":\"" + code
                + "\",\"error\":\"" + error
                + "\",\"message\":\"" + message + "\"}");
        writer.flush();
    }
}
