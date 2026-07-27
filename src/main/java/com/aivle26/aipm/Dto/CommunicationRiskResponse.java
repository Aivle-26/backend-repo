package com.aivle26.aipm.Dto;

import com.aivle26.aipm.Entity.CommunicationAnalysisStatus;
import com.aivle26.aipm.Entity.CommunicationLlmStatus;
import com.aivle26.aipm.Entity.CommunicationRiskLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프론트로 내보내는 커뮤니케이션 리스크 응답.
 *
 * <p>AI 서버 응답을 거의 그대로 통과시키되 camelCase로 바꾸고,
 * 분석 이력이 없는 경우를 구분할 status와 마지막 분석 시각을 추가했다.
 * 스펙: frontend-repo/docs/API-커뮤니케이션리스크-스펙초안.md
 */
public record CommunicationRiskResponse(
        Long projectId,
        String projectName,
        CommunicationAnalysisStatus status,
        CommunicationRiskLevel riskLevel,
        List<String> reasons,
        List<EvidenceMessage> evidenceMessages,
        String recommendedAction,
        Metrics metrics,
        AnalysisWindow analysisWindow,
        CommunicationLlmStatus llmStatus,
        LocalDateTime analyzedAt
) {

    public record EvidenceMessage(
            String channelId,
            String channelName,
            LocalDateTime messageTs,
            String threadTs,
            String messageText
    ) {
    }

    public record Metrics(
            int recent7dMessageCount,
            int previous7dMessageCount,
            Double activityChangePercent,
            int longUnansweredCount
    ) {
    }

    public record AnalysisWindow(
            LocalDateTime start,
            LocalDateTime end
    ) {
    }

    /** 아직 분석한 적 없음. 프론트는 이 상태에서 '분석 시작' 버튼을 띄운다. */
    public static CommunicationRiskResponse neverAnalyzed(Long projectId, String projectName) {
        return new CommunicationRiskResponse(
                projectId, projectName,
                CommunicationAnalysisStatus.NEVER_ANALYZED,
                null, List.of(), List.of(), null,
                new Metrics(0, 0, null, 0),
                null, null, null);
    }
}
