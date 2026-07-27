package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiImpactAnalysisRequest;
import com.aivle26.aipm.Dto.AiImpactAnalysisResponse;
import com.aivle26.aipm.Dto.ImpactAnalysisRequest;
import com.aivle26.aipm.Dto.ImpactAnalysisResponse;
import com.aivle26.aipm.Entity.Project;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 조정 여부 평가(요구사항 변경 영향도).
 *
 * <p>AI 서버가 무상태 계산기라, 백엔드는 프로젝트 존재 확인 후
 * 프론트가 넘긴 변경 정보에 projectId를 더해 AI로 포워딩하고 응답을 매핑한다.
 * (저장이 필요해지면 결과 엔티티를 추가하면 되지만, 현재는 온디맨드 계산이라 저장하지 않는다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactAnalysisService {

    private final ProjectRepository projectRepository;
    private final ImpactAnalysisAgentClient agentClient;

    @Transactional(readOnly = true)
    public ImpactAnalysisResponse assess(Long projectId, ImpactAnalysisRequest request) {
        Project project = findProject(projectId);

        AiImpactAnalysisRequest aiRequest = new AiImpactAnalysisRequest(
                project.getId(),
                request.requirementId(),
                request.changeTitle(),
                request.changeDescription(),
                request.affectedTaskCount(),
                request.affectedMemberCount(),
                request.remainingDays(),
                request.additionalWorkDays(),
                request.scopeChanged(),
                request.databaseChanged(),
                request.apiChanged(),
                request.uiChanged());

        AiImpactAnalysisResponse aiResponse = agentClient.assess(aiRequest);
        return ImpactAnalysisResponse.from(aiResponse);
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }
}
