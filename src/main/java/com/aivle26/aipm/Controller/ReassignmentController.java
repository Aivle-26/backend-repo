package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.ReassignmentRequest;
import com.aivle26.aipm.Dto.ReassignmentResponse;
import com.aivle26.aipm.Service.ReassignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 담당자 재배정 추천 API.
 *
 * <p>/api/projects/** 는 SecurityConfig에서 인증 필수 구간이라
 * 프론트는 JWT(Authorization: Bearer)만 실어 보내면 된다.
 * assignmentId는 재배정을 평가할 대상 업무(AI의 task_id)로 사용한다.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ReassignmentController {

    private final ReassignmentService reassignmentService;

    /**
     * 재배정 필요 여부와 추천 후보를 반환한다.
     *
     * <p>body가 있으면 그 사람 데이터를, 없으면 DB의 더미 팀원 로스터를 사용한다.
     * 즉 빈 body로 호출하면 시드된 팀원 기준으로 추천이 돈다.
     */
    @PostMapping("/{projectId}/assignments/{assignmentId}")
    public ResponseEntity<ReassignmentResponse> recommend(
            @PathVariable Long projectId,
            @PathVariable Long assignmentId,
            @RequestBody(required = false) ReassignmentRequest request) {
        return ResponseEntity.ok(reassignmentService.recommend(projectId, assignmentId, request));
    }
}
