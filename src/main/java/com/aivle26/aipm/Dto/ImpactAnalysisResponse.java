package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * 프론트로 내보내는 "프로젝트 조정 여부 평가" 응답.
 *
 * <p>AI 서버 응답을 거의 그대로 통과시키되 camelCase로 바꾼다.
 * impactLevel은 AI 서버 등급 문자열(LOW/MEDIUM/HIGH/CRITICAL)을 그대로 전달한다.
 *
 * <p>llmStatus=SUCCEEDED이면 affected*, remainingDays, additionalWorkDays,
 * *Changed 필드는 AI가 자동 산출한 값이다(프론트 폼 자동 입력용).
 * 그 외에는 요청에 담겨온 입력값이 그대로 반영된다.
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
        List<String> recommendedActions,

        // --- AI 산출 결과 ---
        String llmStatus,
        String aiSummary,
        int affectedTaskCount,
        int affectedMemberCount,
        int remainingDays,
        int additionalWorkDays,
        boolean scopeChanged,
        boolean databaseChanged,
        boolean apiChanged,
        boolean uiChanged,
        List<AffectedTask> affectedTasks
) {

    /** 화면 표시용 영향 태스크. */
    public record AffectedTask(
            Long taskId,
            String taskName,
            String impactType,
            int additionalWorkDays,
            String reason
    ) {
    }

    /** AI 서버 응답(snake_case DTO) → 프론트 응답(camelCase). */
    public static ImpactAnalysisResponse from(AiImpactAnalysisResponse ai) {
        List<AffectedTask> affected = ai.affectedTasks() == null
                ? List.of()
                : ai.affectedTasks().stream()
                        .map(task -> new AffectedTask(
                                task.taskId(),
                                task.taskName(),
                                task.impactType(),
                                task.additionalWorkDays(),
                                task.reason()))
                        .toList();

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
                ai.recommendedActions() == null ? List.of() : ai.recommendedActions(),
                ai.llmStatus(),
                ai.aiSummary(),
                ai.affectedTaskCount(),
                ai.affectedMemberCount(),
                ai.remainingDays(),
                ai.additionalWorkDays(),
                ai.scopeChanged(),
                ai.databaseChanged(),
                ai.apiChanged(),
                ai.uiChanged(),
                affected);
    }
}
