package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AiServerProperties;
import com.aivle26.aipm.Dto.AiCommunicationRiskRequest;
import com.aivle26.aipm.Dto.AiCommunicationRiskResponse;
import com.aivle26.aipm.Dto.CommunicationRiskResponse;
import com.aivle26.aipm.Entity.CommunicationAnalysisStatus;
import com.aivle26.aipm.Entity.CommunicationLlmStatus;
import com.aivle26.aipm.Entity.CommunicationRiskLevel;
import com.aivle26.aipm.Entity.CommunicationRiskResult;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Entity.ProjectSlackChannel;
import com.aivle26.aipm.Entity.SlackMessageThread;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.CommunicationRiskResultRepository;
import com.aivle26.aipm.Repository.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectSlackChannelRepository;
import com.aivle26.aipm.Repository.SlackMessageThreadRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 커뮤니케이션 리스크 조회 · 재분석.
 *
 * <pre>
 * refresh: Slack 증분 수집 → (새 메시지 있으면) AI 서버 호출 → 저장 → 반환
 * get:     저장된 최신 결과 반환
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationRiskService {

    /** AI 서버가 최근 7일과 이전 7일을 비교하므로 14일치를 넘긴다. */
    private static final int ANALYSIS_WINDOW_DAYS = 14;

    private final ProjectRepository projectRepository;
    private final ProjectSlackChannelRepository projectSlackChannelRepository;
    private final SlackMessageThreadRepository slackMessageThreadRepository;
    private final CommunicationRiskResultRepository communicationRiskResultRepository;
    private final SlackMessageSyncService slackMessageSyncService;
    private final CommunicationRiskAgentClient agentClient;
    private final AiServerProperties aiServerProperties;
    private final ObjectMapper objectMapper;

    /** 저장된 최신 결과. 이력이 없으면 NEVER_ANALYZED를 반환한다(404 아님). */
    @Transactional(readOnly = true)
    public CommunicationRiskResponse getLatest(Long projectId) {
        Project project = findProject(projectId);
        return communicationRiskResultRepository.findTopByProjectIdOrderByAnalyzedAtDesc(projectId)
                .map(this::toResponse)
                .orElseGet(() -> CommunicationRiskResponse.neverAnalyzed(projectId, project.getName()));
    }

    /**
     * Slack 새 메시지를 수집하고 재분석한다.
     *
     * <p>새 메시지가 없으면 AI 서버를 호출하지 않고 기존 결과를 그대로 반환한다.
     * LLM은 비용과 지연이 있어 같은 입력으로 다시 부를 이유가 없다.
     */
    @Transactional
    public CommunicationRiskResponse refresh(Long projectId) {
        Project project = findProject(projectId);

        int newMessages = slackMessageSyncService.syncProject(projectId);
        var existing = communicationRiskResultRepository.findTopByProjectIdOrderByAnalyzedAtDesc(projectId);

        if (newMessages == 0 && existing.isPresent()) {
            log.debug("프로젝트 {}: 새 메시지 없음, 기존 결과 반환", projectId);
            return toResponse(existing.get());
        }

        LocalDateTime analysisEnd = LocalDateTime.now();
        List<SlackMessageThread> messages = loadMessages(projectId, analysisEnd);

        if (messages.isEmpty()) {
            // AI 서버는 messages를 1건 이상 요구한다. 빈 채로 부르면 422가 난다.
            log.info("프로젝트 {}: 분석 창({}일) 내 메시지가 없어 분석을 건너뜀", projectId, ANALYSIS_WINDOW_DAYS);
            return CommunicationRiskResponse.neverAnalyzed(projectId, project.getName());
        }

        AiCommunicationRiskResponse aiResponse =
                agentClient.analyze(buildRequest(project, analysisEnd, messages));

        return toResponse(save(project, aiResponse));
    }

    private List<SlackMessageThread> loadMessages(Long projectId, LocalDateTime analysisEnd) {
        List<String> channelIds = projectSlackChannelRepository.findByProjectId(projectId).stream()
                .map(ProjectSlackChannel::getChannelId)
                .toList();
        if (channelIds.isEmpty()) {
            return List.of();
        }
        return slackMessageThreadRepository.findByChannelIdInAndMessageTsBetweenOrderByMessageTsAsc(
                channelIds, analysisEnd.minusDays(ANALYSIS_WINDOW_DAYS), analysisEnd);
    }

    private AiCommunicationRiskRequest buildRequest(
            Project project, LocalDateTime analysisEnd, List<SlackMessageThread> messages) {
        List<AiCommunicationRiskRequest.AiSlackMessage> payload = messages.stream()
                .map(m -> new AiCommunicationRiskRequest.AiSlackMessage(
                        m.getChannelId(),
                        m.getChannelName(),
                        m.getMessageTs(),
                        m.getThreadTs(),
                        m.getUserId(),
                        m.getMessageText(),
                        m.getReplyCount(),
                        m.getMentionCount(),
                        m.getReactionSummary() == null ? "" : m.getReactionSummary(),
                        m.getFileCount()))
                .toList();

        return new AiCommunicationRiskRequest(
                project.getId(),
                project.getName(),
                analysisEnd,
                aiServerProperties.isEnableLlm(),
                payload);
    }

    private CommunicationRiskResult save(Project project, AiCommunicationRiskResponse ai) {
        CommunicationRiskResult result = new CommunicationRiskResult();
        result.setProject(project);
        result.setRiskLevel(parseEnum(CommunicationRiskLevel.class, ai.communicationRiskLevel(),
                CommunicationRiskLevel.LOW));
        result.setReasonsJson(writeJson(ai.reasons()));
        // AI 서버는 근거 메시지를 snake_case(channel_id)로 준다. 저장 시점에 응답용
        // 타입으로 변환해 camelCase로 박아야, 조회 시 EvidenceMessage로 그대로 읽힌다.
        // (변환을 안 하면 필드명이 안 맞아 조회 결과가 전부 null이 된다)
        result.setEvidenceMessagesJson(writeJson(toEvidenceMessages(ai.evidenceMessages())));
        result.setRecommendedAction(ai.recommendedAction() == null ? "" : ai.recommendedAction());

        var metrics = ai.metrics();
        result.setRecent7dMessageCount(metrics == null ? 0 : metrics.recent7dMessageCount());
        result.setPrevious7dMessageCount(metrics == null ? 0 : metrics.previous7dMessageCount());
        result.setActivityChangePercent(metrics == null ? null : metrics.activityChangePercent());
        result.setLongUnansweredCount(metrics == null ? 0 : metrics.longUnansweredCount());

        var window = ai.analysisWindow();
        LocalDateTime now = LocalDateTime.now();
        result.setAnalysisWindowStart(window == null || window.get("start") == null ? now : window.get("start"));
        result.setAnalysisWindowEnd(window == null || window.get("end") == null ? now : window.get("end"));
        result.setLlmStatus(parseEnum(CommunicationLlmStatus.class, ai.llmStatus(),
                CommunicationLlmStatus.DISABLED));

        return communicationRiskResultRepository.save(result);
    }

    private CommunicationRiskResponse toResponse(CommunicationRiskResult r) {
        List<String> reasons = readJson(r.getReasonsJson(), new TypeReference<>() {});
        List<CommunicationRiskResponse.EvidenceMessage> evidence =
                readJson(r.getEvidenceMessagesJson(), new TypeReference<>() {});

        return new CommunicationRiskResponse(
                r.getProject().getId(),
                r.getProject().getName(),
                CommunicationAnalysisStatus.COMPLETED,
                r.getRiskLevel(),
                reasons,
                evidence,
                r.getRecommendedAction(),
                new CommunicationRiskResponse.Metrics(
                        r.getRecent7dMessageCount(),
                        r.getPrevious7dMessageCount(),
                        r.getActivityChangePercent(),
                        r.getLongUnansweredCount()),
                new CommunicationRiskResponse.AnalysisWindow(
                        r.getAnalysisWindowStart(),
                        r.getAnalysisWindowEnd()),
                r.getLlmStatus(),
                r.getAnalyzedAt());
    }

    /** AI 서버 근거 메시지(snake_case) → 응답/저장용 타입(camelCase). */
    private List<CommunicationRiskResponse.EvidenceMessage> toEvidenceMessages(
            List<AiCommunicationRiskResponse.AiEvidenceMessage> aiMessages) {
        if (aiMessages == null) {
            return List.of();
        }
        return aiMessages.stream()
                .map(m -> new CommunicationRiskResponse.EvidenceMessage(
                        m.channelId(),
                        m.channelName(),
                        m.messageTs(),
                        m.threadTs(),
                        m.messageText()))
                .toList();
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }

    /** AI 서버가 예상 밖 문자열을 주더라도 500으로 죽지 않도록 기본값으로 흡수한다. */
    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("AI 서버가 알 수 없는 {} 값을 반환: {}", type.getSimpleName(), value);
            return fallback;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "분석 결과 직렬화 실패", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "분석 결과 역직렬화 실패", e);
        }
    }
}
