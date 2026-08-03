package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.MemberDelayResponse;
import com.aivle26.aipm.Service.MemberDelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀원별 업무 진행 지연 분석 API.
 *
 * <p>/api/projects/** 는 SecurityConfig에서 인증 필수 구간이라
 * 프론트는 JWT(Authorization: Bearer)만 실어 보내면 된다.
 * 팀원 업무 현황은 백엔드가 DB에서 모으므로 요청 body는 없다.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PM')")
public class MemberDelayController {

    private final MemberDelayService memberDelayService;

    /** 프로젝트 팀원들의 지연 위험 점수·등급·근거를 반환한다. */
    @PostMapping("/{projectId}/member-delay")
    public ResponseEntity<MemberDelayResponse> analyze(@PathVariable Long projectId) {
        return ResponseEntity.ok(memberDelayService.analyze(projectId));
    }
}
