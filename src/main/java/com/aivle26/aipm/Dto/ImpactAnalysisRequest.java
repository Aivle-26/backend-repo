package com.aivle26.aipm.Dto;

/**
 * 프론트가 보내는 "프로젝트 조정 여부 평가" 요청 body.
 *
 * <p>projectId는 경로 변수로 받으므로 여기 없다. PM이 화면 폼에 입력하는
 * 변경 정보(제목/설명/영향 범위)를 그대로 담아 백엔드가 AI 서버로 넘긴다.
 * requirementId는 특정 요구사항 변경을 평가할 때만 채우고, 아니면 null.
 *
 * <p>useLlm=true이면("AI 분석") 백엔드가 확정 WBS를 모아 AI에 넘겨 수치를
 * 자동 산출한다. false이면("평가하기") 아래 수동 입력 수치로 규칙 계산한다.
 * (null은 true로 간주해 AI 경로를 기본으로 한다)
 */
public record ImpactAnalysisRequest(
        Long requirementId,
        String changeTitle,
        String changeDescription,
        int affectedTaskCount,
        int affectedMemberCount,
        int remainingDays,
        int additionalWorkDays,
        boolean scopeChanged,
        boolean databaseChanged,
        boolean apiChanged,
        boolean uiChanged,
        Boolean useLlm
) {

    /** null이면 AI 경로를 기본으로 한다. */
    public boolean useLlmOrDefault() {
        return useLlm == null || useLlm;
    }
}
