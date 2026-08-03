package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.SecurityCheckRequest;
import com.aivle26.aipm.Dto.SecurityCheckResponse;
import com.aivle26.aipm.Service.SecurityCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 산출물 보안 검사 API.
 *
 * <p>/api/projects/** 는 SecurityConfig에서 인증 필수 구간이라
 * 프론트는 JWT(Authorization: Bearer)만 실어 보내면 된다.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PM')")
public class SecurityCheckController {

    private final SecurityCheckService securityCheckService;

    /** 산출물 본문에서 개인정보·인증정보를 탐지·마스킹하고 등록 가능 여부를 반환한다. */
    @PostMapping("/{projectId}/deliverables/{deliverableId}/security-check")
    public ResponseEntity<SecurityCheckResponse> check(
            @PathVariable Long projectId,
            @PathVariable Long deliverableId,
            @RequestBody SecurityCheckRequest request) {
        return ResponseEntity.ok(securityCheckService.inspect(projectId, deliverableId, request));
    }
}
