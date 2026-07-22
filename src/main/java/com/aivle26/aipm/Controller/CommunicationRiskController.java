package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.CommunicationRiskResponse;
import com.aivle26.aipm.Service.CommunicationRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slack 커뮤니케이션 리스크 API.
 *
 * <p>/api/projects/** 는 SecurityConfig에서 인증 필수 구간이라
 * 프론트는 JWT(Authorization: Bearer)만 실어 보내면 된다.
 * Slack 토큰과 채널 매핑은 백엔드가 보관하므로 session 파라미터는 받지 않는다.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class CommunicationRiskController {

    private final CommunicationRiskService communicationRiskService;

    /** 저장된 최신 분석 결과. 이력이 없으면 status=NEVER_ANALYZED. */
    @GetMapping("/{projectId}/communication-risks")
    public ResponseEntity<CommunicationRiskResponse> get(@PathVariable Long projectId) {
        return ResponseEntity.ok(communicationRiskService.getLatest(projectId));
    }

    /** Slack 새 메시지를 증분 수집하고 재분석. 새 메시지가 없으면 기존 결과 반환. */
    @PostMapping("/{projectId}/communication-risks/refresh")
    public ResponseEntity<CommunicationRiskResponse> refresh(@PathVariable Long projectId) {
        return ResponseEntity.ok(communicationRiskService.refresh(projectId));
    }
}
