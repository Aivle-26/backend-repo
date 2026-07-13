package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.LoginRequest;
import com.aivle26.aipm.Dto.LoginResponse;
import com.aivle26.aipm.Dto.PasswordChangeRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckRequest;
import com.aivle26.aipm.Dto.PasswordEmailCheckResponse;
import com.aivle26.aipm.Dto.PasswordEmailSendRequest;
import com.aivle26.aipm.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
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
