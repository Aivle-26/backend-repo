package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.AuthRefreshRequest;
import com.aivle26.aipm.Dto.AuthSessionResponse;
import com.aivle26.aipm.Dto.LoginRequest;
import com.aivle26.aipm.Dto.LoginResendRequest;
import com.aivle26.aipm.Dto.LoginResponse;
import com.aivle26.aipm.Dto.LoginVerifyRequest;
import com.aivle26.aipm.Dto.LoginVerifyResponse;
import com.aivle26.aipm.Dto.LogoutRequest;
import com.aivle26.aipm.Dto.PasswordChangeRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckResponse;
import com.aivle26.aipm.Dto.PasswordEmailSendRequest;
import com.aivle26.aipm.Dto.SignupRequest;
import com.aivle26.aipm.Dto.SignupResponse;
import com.aivle26.aipm.Service.AuthService;
import com.aivle26.aipm.Service.AuthenticatedUser;
import com.aivle26.aipm.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(201).body(userService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/login/verify")
    public ResponseEntity<LoginVerifyResponse> verifyLogin(@Valid @RequestBody LoginVerifyRequest request) {
        return ResponseEntity.ok(userService.verifyLogin(request));
    }

    @GetMapping("/session")
    public ResponseEntity<AuthSessionResponse> session(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getCurrentSession(user.employeeNumber()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthSessionResponse> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getCurrentSession(user.employeeNumber()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/activity")
    public ResponseEntity<Map<String, String>> recordActivity(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        authService.recordActivity(user.employeeNumber());
        return ResponseEntity.ok(Map.of("message", "activity recorded"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(Authentication authentication, @RequestBody(required = false) LogoutRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            authService.logout(user.employeeNumber(), request == null ? null : request.refreshToken());
        } else if (request != null) {
            authService.logoutByRefreshToken(request.refreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "logged out"));
    }

    @PostMapping("/login/resend")
    public ResponseEntity<LoginResponse> resendLoginVerification(@Valid @RequestBody LoginResendRequest request) {
        return ResponseEntity.ok(userService.resendLoginVerification(request));
    }

    @PostMapping("/password/email-send")
    public ResponseEntity<Map<String, String>> sendEmailCode(@Valid @RequestBody PasswordEmailSendRequest request) {
        userService.sendPasswordEmailCode(request);
        return ResponseEntity.ok(Map.of("message", "verification code sent"));
    }

    @PostMapping("/password/email-check")
    public ResponseEntity<PasswordEmailCheckResponse> verifyEmailCode(@Valid @RequestBody PasswordEmailCheckRequest request) {
        return ResponseEntity.ok(userService.verifyPasswordEmailCode(request));
    }

    @PatchMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "password changed"));
    }
}
