package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * 프론트가 보내는 "담당자 재배정 추천" 요청 body.
 *
 * <p>projectId와 assignmentId(=taskId)는 경로 변수로 받으므로 여기 없다.
 * 현재 담당자와 후보들의 스킬·업무량·지연 정보를 담아 백엔드가 AI 서버로 넘긴다.
 */
public record ReassignmentRequest(
        String taskName,
        String requiredRole,
        List<String> requiredSkills,
        Assignee currentAssignee,
        List<Candidate> candidates
) {
    /** 현재 담당자. */
    public record Assignee(
            Long memberId,
            String memberName,
            List<String> skills,
            double workloadRate,
            int overdueTaskCount
    ) {
    }

    /** 재배정 후보. */
    public record Candidate(
            Long memberId,
            String memberName,
            String role,
            List<String> skills,
            double workloadRate,
            int overdueTaskCount
    ) {
    }
}
