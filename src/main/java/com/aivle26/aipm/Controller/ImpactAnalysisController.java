package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.ImpactAnalysisRequest;
import com.aivle26.aipm.Dto.ImpactAnalysisResponse;
import com.aivle26.aipm.Service.ImpactAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 조정 여부 평가 API (요구사항 변경 영향도).
 *
 * <p>/api/projects/** 는 SecurityConfig에서 인증 필수 구간이라
 * 프론트는 JWT(Authorization: Bearer)만 실어 보내면 된다.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PM')")
public class ImpactAnalysisController {

    private final ImpactAnalysisService impactAnalysisService;

    /** 변경 정보를 받아 영향도 점수/등급/권고를 반환한다. */
    @PostMapping("/{projectId}/impact-analysis")
    public ResponseEntity<ImpactAnalysisResponse> analyze(
            @PathVariable Long projectId,
            @RequestBody ImpactAnalysisRequest request) {
        return ResponseEntity.ok(impactAnalysisService.assess(projectId, request));
    }
}
