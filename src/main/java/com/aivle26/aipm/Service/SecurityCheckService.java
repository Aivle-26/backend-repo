package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.AiSecurityCheckRequest;
import com.aivle26.aipm.Dto.AiSecurityCheckResponse;
import com.aivle26.aipm.Dto.SecurityCheckRequest;
import com.aivle26.aipm.Dto.SecurityCheckResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 산출물 보안 검사.
 *
 * <p>AI 서버가 무상태 검사기라, 백엔드는 프로젝트 존재 확인 후
 * 프론트가 넘긴 산출물 텍스트에 projectId를 더해 AI로 포워딩하고 응답을 매핑한다.
 * (deliverableId는 REST 리소스 식별자로만 쓰고 AI 요청엔 넣지 않는다.
 *  추후 저장된 산출물 본문을 deliverableId로 조회해 채우도록 확장 가능하다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityCheckService {

    private final ProjectRepository projectRepository;
    private final SecurityCheckAgentClient agentClient;
    private final ProjectAuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public SecurityCheckResponse inspect(Long projectId, Long deliverableId, SecurityCheckRequest request) {
        authorizationService.requireProjectPm(projectId);
        Project project = findProject(projectId);

        if (request == null || request.textContent() == null || request.textContent().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "검사할 산출물 본문(textContent)이 필요합니다.");
        }

        AiSecurityCheckRequest aiRequest = new AiSecurityCheckRequest(
                project.getId(),
                request.artifactName(),
                request.artifactType(),
                request.textContent());

        AiSecurityCheckResponse aiResponse = agentClient.inspect(aiRequest);
        return SecurityCheckResponse.from(aiResponse);
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }
}
