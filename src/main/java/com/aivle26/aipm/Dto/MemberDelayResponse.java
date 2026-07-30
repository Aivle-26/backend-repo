package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * 프론트로 내보내는 "팀원별 업무 지연 분석" 응답. (camelCase)
 */
public record MemberDelayResponse(
        Long projectId,
        int analyzedMemberCount,
        int highRiskMemberCount,
        List<MemberResult> memberResults
) {

    public record MemberResult(
            Long memberId,
            String memberName,
            double completionRate,
            double overdueRate,
            int delayScore,
            String riskLevel,
            List<String> reasons,
            String recommendedAction
    ) {
        static MemberResult from(AiMemberDelayResponse.AiMemberResult ai) {
            return new MemberResult(
                    ai.memberId(),
                    ai.memberName(),
                    ai.completionRate(),
                    ai.overdueRate(),
                    ai.delayScore(),
                    ai.riskLevel(),
                    ai.reasons() == null ? List.of() : ai.reasons(),
                    ai.recommendedAction());
        }
    }

    /** AI 서버 응답(snake_case DTO) → 프론트 응답(camelCase). */
    public static MemberDelayResponse from(AiMemberDelayResponse ai) {
        List<MemberResult> results = ai.memberResults() == null
                ? List.of()
                : ai.memberResults().stream().map(MemberResult::from).toList();

        return new MemberDelayResponse(
                ai.projectId(),
                ai.analyzedMemberCount(),
                ai.highRiskMemberCount(),
                results);
    }
}
