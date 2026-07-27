package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * 프론트로 내보내는 "프로젝트 조정 여부 평가" 응답.
 *
 * <p>AI 서버 응답을 거의 그대로 통과시키되 camelCase로 바꾼다.
 * impactLevel은 AI 서버 등급 문자열(LOW/MEDIUM/HIGH/CRITICAL)을 그대로 전달한다.
 */
public record ImpactAnalysisResponse(
        Long projectId,
        Long requirementId,
        int impactScore,
        String impactLevel,
        int scheduleImpactScore,
        int scopeImpactScore,
        int resourceImpactScore,
        int technicalImpactScore,
        List<String> riskFactors,
        List<String> recommendedActions
) {

    /** AI 서버 응답(snake_case DTO) → 프론트 응답(camelCase). */
    public static ImpactAnalysisResponse from(AiImpactAnalysisResponse ai) {
        return new ImpactAnalysisResponse(
                ai.projectId(),
                ai.requirementId(),
                ai.impactScore(),
                ai.impactLevel(),
                ai.scheduleImpactScore(),
                ai.scopeImpactScore(),
                ai.resourceImpactScore(),
                ai.technicalImpactScore(),
                ai.riskFactors() == null ? List.of() : ai.riskFactors(),
                ai.recommendedActions() == null ? List.of() : ai.recommendedActions());
    }
}
