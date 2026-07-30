package com.aivle26.aipm.Controller.user;

import com.aivle26.aipm.Dto.auth.AuthRefreshRequest;
import com.aivle26.aipm.Dto.auth.AuthSessionResponse;
import com.aivle26.aipm.Dto.auth.LoginRequest;
import com.aivle26.aipm.Dto.auth.LoginResendRequest;
import com.aivle26.aipm.Dto.auth.LoginResponse;
import com.aivle26.aipm.Dto.auth.LoginVerifyRequest;
import com.aivle26.aipm.Dto.auth.LoginVerifyResponse;
import com.aivle26.aipm.Dto.auth.LogoutRequest;
import com.aivle26.aipm.Dto.auth.PasswordChangeRequest;
import com.aivle26.aipm.Dto.auth.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.auth.PasswordEmailCheckResponse;
import com.aivle26.aipm.Dto.auth.PasswordEmailSendRequest;
import com.aivle26.aipm.Dto.auth.SignupRequest;
import com.aivle26.aipm.Dto.auth.SignupResponse;
import com.aivle26.aipm.Dto.auth.SignupStartResponse;
import com.aivle26.aipm.Dto.auth.SignupVerifyRequest;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import com.aivle26.aipm.Service.auth.AuthService;
import com.aivle26.aipm.Service.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final AuthService authService;

    // 가입 정보를 검증해 이메일 인증을 시작하고 안내 응답을 반환한다.
    @PostMapping("/signup")
    public ResponseEntity<SignupStartResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(userService.signup(request));
    }

    // 가입 인증 코드를 확인해 사용자를 생성하고 가입 결과를 반환한다.
    @PostMapping("/signup/verify")
    public ResponseEntity<SignupResponse> verifySignup(@Valid @RequestBody SignupVerifyRequest request) {
        return ResponseEntity.status(201).body(userService.verifySignup(request));
    }

    // 로그인 자격 증명을 확인해 Access·Refresh Token이 포함된 세션을 반환한다.
    @PostMapping("/login")
    public ResponseEntity<LoginVerifyResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // 로그인 인증 코드를 검증해 인증 세션 정보를 반환한다.
    @PostMapping("/login/verify")
    public ResponseEntity<LoginVerifyResponse> verifyLogin(@Valid @RequestBody LoginVerifyRequest request) {
        return ResponseEntity.ok(userService.verifyLogin(request));
    }

    // 현재 인증 사용자의 유효한 세션 정보를 조회해 반환한다.
    @GetMapping("/session")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<AuthSessionResponse> session(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getCurrentSession(user.employeeNumber()));
    }

    // 현재 인증 사용자의 세션 기반 사용자 정보를 반환한다.
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<AuthSessionResponse> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getCurrentSession(user.employeeNumber()));
    }

    // 리프레시 토큰을 검증해 갱신된 인증 세션을 반환한다.
    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    // 인증 사용자 또는 리프레시 토큰의 세션을 종료하고 결과를 반환한다.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(Authentication authentication, @RequestBody(required = false) LogoutRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            authService.logout(user.employeeNumber(), request == null ? null : request.refreshToken());
        } else if (request != null) {
            authService.logoutByRefreshToken(request.refreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "logged out"));
    }

    // 로그인 이메일 인증 코드를 재발급하고 전송 결과를 반환한다.
    @PostMapping("/login/resend")
    public ResponseEntity<LoginResponse> resendLoginVerification(@Valid @RequestBody LoginResendRequest request) {
        return ResponseEntity.ok(userService.resendLoginVerification(request));
    }

    // 비밀번호 재설정 대상 이메일에 인증 코드를 발급해 전송한다.
    @PostMapping("/password/email-send")
    public ResponseEntity<Map<String, String>> sendEmailCode(@Valid @RequestBody PasswordEmailSendRequest request) {
        userService.sendPasswordEmailCode(request);
        return ResponseEntity.ok(Map.of("message", "verification code sent"));
    }

    // 비밀번호 재설정 인증 코드를 확인해 재설정 토큰을 반환한다.
    @PostMapping("/password/email-check")
    public ResponseEntity<PasswordEmailCheckResponse> verifyEmailCode(@Valid @RequestBody PasswordEmailCheckRequest request) {
        return ResponseEntity.ok(userService.verifyPasswordEmailCode(request));
    }

    // 재설정 토큰을 검증해 사용자 비밀번호를 변경한다.
    @PatchMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "password changed"));
    }
}
