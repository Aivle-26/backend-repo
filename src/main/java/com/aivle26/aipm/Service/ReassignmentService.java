package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiReassignmentRequest;
import com.aivle26.aipm.Dto.AiReassignmentResponse;
import com.aivle26.aipm.Dto.ReassignmentRequest;
import com.aivle26.aipm.Dto.ReassignmentResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.risk.RiskTeamMember;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.risk.RiskTeamMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 담당자 재배정 추천.
 *
 * <p>AI 서버가 무상태 계산기라, 백엔드는 프로젝트 존재 확인 후
 * 현재 담당자·후보 정보에 projectId/taskId를 더해 AI로 포워딩하고 응답을 매핑한다.
 *
 * <p>사람 데이터 소스는 두 가지다:
 * <ol>
 *   <li>요청 body에 currentAssignee/candidates가 오면 그대로 사용(테스트·수동 입력).</li>
 *   <li>body가 비어 있으면 DB의 더미 팀원(RiskTeamMember)으로 자동 구성 —
 *       "요구사항 → 업무배정 → 추천" 흐름 검증용. 실제 assignment 도메인이 생기면
 *       이 폴백을 그 데이터 조회로 교체한다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReassignmentService {

    private final ProjectRepository projectRepository;
    private final RiskTeamMemberRepository riskTeamMemberRepository;
    private final ReassignmentAgentClient agentClient;

    @Transactional(readOnly = true)
    public ReassignmentResponse recommend(Long projectId, Long taskId, ReassignmentRequest request) {
        Project project = findProject(projectId);

        AiReassignmentRequest aiRequest = hasPeople(request)
                ? fromBody(project, taskId, request)
                : fromDummyRoster(project, taskId);

        AiReassignmentResponse aiResponse = agentClient.recommend(aiRequest);
        return ReassignmentResponse.from(aiResponse);
    }

    private boolean hasPeople(ReassignmentRequest request) {
        return request != null
                && request.currentAssignee() != null
                && request.candidates() != null
                && !request.candidates().isEmpty();
    }

    /* ---------- 1) body 기반 ---------- */

    private AiReassignmentRequest fromBody(Project project, Long taskId, ReassignmentRequest request) {
        return new AiReassignmentRequest(
                project.getId(),
                taskId,
                request.taskName(),
                request.requiredRole(),
                request.requiredSkills() == null ? List.of() : request.requiredSkills(),
                new AiReassignmentRequest.AiCurrentAssignee(
                        request.currentAssignee().memberId(),
                        request.currentAssignee().memberName(),
                        request.currentAssignee().skills() == null ? List.of() : request.currentAssignee().skills(),
                        request.currentAssignee().workloadRate(),
                        request.currentAssignee().overdueTaskCount()),
                request.candidates().stream()
                        .map(c -> new AiReassignmentRequest.AiCandidateMember(
                                c.memberId(), c.memberName(), c.role(),
                                c.skills() == null ? List.of() : c.skills(),
                                c.workloadRate(), c.overdueTaskCount()))
                        .toList());
    }

    /* ---------- 2) DB 더미 로스터 폴백 ---------- */

    private AiReassignmentRequest fromDummyRoster(Project project, Long taskId) {
        List<RiskTeamMember> roster = riskTeamMemberRepository.findByProjectId(project.getId());
        if (roster.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_TEAM_MEMBERS",
                    "프로젝트에 팀원 데이터가 없습니다. (local 시드 또는 요청 body 필요)");
        }

        RiskTeamMember current = roster.stream()
                .filter(RiskTeamMember::isCurrentAssignee)
                .findFirst()
                .orElse(roster.get(0));

        List<AiReassignmentRequest.AiCandidateMember> candidates = roster.stream()
                .filter(m -> !m.getId().equals(current.getId()))
                .map(m -> new AiReassignmentRequest.AiCandidateMember(
                        m.getId(), m.getMemberName(), m.getRole(),
                        splitSkills(m.getSkills()), m.getWorkloadRate(), m.getOverdueTaskCount()))
                .toList();

        // 더미: 실제로는 WBS 업무(taskId)의 requiredSkills·역할에서 온다.
        return new AiReassignmentRequest(
                project.getId(),
                taskId,
                "재배정 검토 업무 #" + taskId,
                current.getRole(),
                List.of("Java", "Spring"),
                new AiReassignmentRequest.AiCurrentAssignee(
                        current.getId(), current.getMemberName(), splitSkills(current.getSkills()),
                        current.getWorkloadRate(), current.getOverdueTaskCount()),
                candidates);
    }

    private List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }
}
