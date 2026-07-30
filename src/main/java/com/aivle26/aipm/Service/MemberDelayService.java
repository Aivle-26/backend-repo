package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiMemberDelayRequest;
import com.aivle26.aipm.Dto.AiMemberDelayResponse;
import com.aivle26.aipm.Dto.MemberDelayResponse;
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

import java.util.List;

/**
 * 팀원별 업무 진행 지연 분석.
 *
 * <p>AI 서버가 무상태 계산기라, 백엔드는 프로젝트 존재 확인 후
 * DB의 팀원(RiskTeamMember) 업무 현황을 모아 AI로 넘기고 응답을 매핑한다.
 * (아직 배정/진척 도메인이 없어 더미 팀원 데이터를 쓰며, 실제 도메인이 생기면
 *  이 조회를 그쪽 데이터로 교체한다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberDelayService {

    private final ProjectRepository projectRepository;
    private final RiskTeamMemberRepository riskTeamMemberRepository;
    private final MemberDelayAgentClient agentClient;

    @Transactional(readOnly = true)
    public MemberDelayResponse analyze(Long projectId) {
        Project project = findProject(projectId);

        List<RiskTeamMember> roster = riskTeamMemberRepository.findByProjectId(project.getId());
        if (roster.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_TEAM_MEMBERS",
                    "프로젝트에 팀원 데이터가 없습니다. (local 시드 필요)");
        }

        List<AiMemberDelayRequest.AiMemberTaskStatus> members = roster.stream()
                .map(m -> new AiMemberDelayRequest.AiMemberTaskStatus(
                        m.getId(),
                        m.getMemberName(),
                        m.getAssignedTaskCount(),
                        m.getCompletedTaskCount(),
                        m.getOverdueTaskCount(),
                        m.getInProgressTaskCount(),
                        m.getAverageDelayDays(),
                        m.getDaysSinceLastUpdate()))
                .toList();

        AiMemberDelayResponse aiResponse =
                agentClient.analyze(new AiMemberDelayRequest(project.getId(), members));
        return MemberDelayResponse.from(aiResponse);
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }
}
