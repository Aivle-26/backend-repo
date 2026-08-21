package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * 프론트로 내보내는 "담당자 재배정 추천" 응답.
 *
 * <p>AI 서버 응답을 camelCase로 통과시킨다.
 * recommendedAssignee는 추천 후보가 없으면 null이다.
 */
public record ReassignmentResponse(
        Long projectId,
        Long taskId,
        boolean reassignmentRequired,
        int currentAssigneeRiskScore,
        String currentAssigneeRiskLevel,
        CandidateResult recommendedAssignee,
        List<CandidateResult> alternativeCandidates,
        List<String> reasons
) {

    public record CandidateResult(
            Long memberId,
            String memberName,
            int matchScore,
            double skillMatchRate,
            double workloadRate,
            int overdueTaskCount,
            String reason
    ) {
        static CandidateResult from(AiReassignmentResponse.AiCandidateResult ai) {
            if (ai == null) {
                return null;
            }
            return new CandidateResult(
                    ai.memberId(),
                    ai.memberName(),
                    ai.matchScore(),
                    ai.skillMatchRate(),
                    ai.workloadRate(),
                    ai.overdueTaskCount(),
                    ai.reason());
        }
    }

    /** AI 서버 응답(snake_case DTO) → 프론트 응답(camelCase). */
    public static ReassignmentResponse from(AiReassignmentResponse ai) {
        List<CandidateResult> alternatives = ai.alternativeCandidates() == null
                ? List.of()
                : ai.alternativeCandidates().stream().map(CandidateResult::from).toList();

        return new ReassignmentResponse(
                ai.projectId(),
                ai.taskId(),
                ai.reassignmentRequired(),
                ai.currentAssigneeRiskScore(),
                ai.currentAssigneeRiskLevel(),
                CandidateResult.from(ai.recommendedAssignee()),
                alternatives,
                ai.reasons() == null ? List.of() : ai.reasons());
    }
}
