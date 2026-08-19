package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Dto.project.WbsGenerationStartResponse;
import com.aivle26.aipm.Dto.project.WbsGenerationStatusResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.project.ProjectStatus;
import com.aivle26.aipm.Entity.project.ProjectWbsGeneration;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.WbsGenerationStatus;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.project.ProjectRequirementRepository;
import com.aivle26.aipm.Repository.project.ProjectWbsGenerationRepository;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectWbsGenerationService {
    private static final int ERROR_CODE_MAX_LENGTH = 100;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final ProjectRepository projectRepository;
    private final ProjectRequirementRepository projectRequirementRepository;
    private final ProjectWbsGenerationRepository generationRepository;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    // 프로젝트 잠금 안에서 진행 중 작업을 확인해 동시에 한 건만 등록한다.
    @Transactional
    public WbsGenerationStartResponse startGeneration(Long projectId) {
        Project project = projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."
                ));
        projectAuthorizationService.requireProjectPm(project);

        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WBS_GENERATION_INVALID_PROJECT_STATUS",
                    "초안 상태의 프로젝트에서만 WBS를 생성할 수 있습니다."
            );
        }
        if (projectRequirementRepository
                .findByProjectIdAndStatus(projectId, RequirementStatus.CONFIRMED)
                .isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WBS_GENERATION_REQUIREMENTS_NOT_FOUND",
                    "확정된 요구사항이 필요합니다."
            );
        }

        ProjectWbsGeneration activeGeneration = generationRepository
                .findFirstByProjectIdAndStatusOrderByCreatedAtDesc(
                        projectId,
                        WbsGenerationStatus.PROCESSING
                )
                .orElse(null);
        if (activeGeneration != null) {
            return toStartResponse(activeGeneration, true);
        }

        AuthenticatedUser user = projectAuthorizationService.currentUser();
        ProjectWbsGeneration generation = new ProjectWbsGeneration();
        generation.setId(UUID.randomUUID().toString());
        generation.setProject(project);
        generation.setStatus(WbsGenerationStatus.PROCESSING);
        generation.setRequestedByEmployeeNumber(user.employeeNumber());
        ProjectWbsGeneration savedGeneration = generationRepository.saveAndFlush(generation);

        eventPublisher.publishEvent(new WbsGenerationRequestedEvent(
                savedGeneration.getId(),
                projectId
        ));
        return toStartResponse(savedGeneration, false);
    }

    @Transactional(readOnly = true)
    public WbsGenerationStatusResponse getGeneration(Long projectId, String generationId) {
        projectAuthorizationService.requireProjectPm(projectId);
        ProjectWbsGeneration generation = generationRepository
                .findByIdAndProjectId(generationId, projectId)
                .orElseThrow(() -> generationNotFound(generationId));
        return toStatusResponse(generation);
    }

    @Transactional(readOnly = true)
    public WbsGenerationStatusResponse getLatestGeneration(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        ProjectWbsGeneration generation = generationRepository
                .findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> generationNotFound(null));
        return toStatusResponse(generation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(String generationId, Long wbsResultId) {
        generationRepository.findById(generationId).ifPresent(generation -> {
            if (generation.getStatus() != WbsGenerationStatus.PROCESSING) {
                return;
            }
            generation.setStatus(WbsGenerationStatus.SUCCEEDED);
            generation.setWbsResultId(wbsResultId);
            generation.setErrorCode(null);
            generation.setErrorMessage(null);
            generation.setCompletedAt(LocalDateTime.now());
            generationRepository.save(generation);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String generationId, String errorCode, String errorMessage) {
        generationRepository.findById(generationId).ifPresent(generation -> {
            if (generation.getStatus() != WbsGenerationStatus.PROCESSING) {
                return;
            }
            generation.setStatus(WbsGenerationStatus.FAILED);
            generation.setErrorCode(truncate(errorCode, ERROR_CODE_MAX_LENGTH));
            generation.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX_LENGTH));
            generation.setCompletedAt(LocalDateTime.now());
            generationRepository.save(generation);
        });
    }

    private WbsGenerationStartResponse toStartResponse(
            ProjectWbsGeneration generation,
            boolean reused
    ) {
        return new WbsGenerationStartResponse(
                generation.getId(),
                generation.getProject().getId(),
                generation.getStatus(),
                reused,
                generation.getCreatedAt()
        );
    }

    private WbsGenerationStatusResponse toStatusResponse(ProjectWbsGeneration generation) {
        return new WbsGenerationStatusResponse(
                generation.getId(),
                generation.getProject().getId(),
                generation.getStatus(),
                generation.getWbsResultId(),
                generation.getErrorCode(),
                generation.getErrorMessage(),
                generation.getCreatedAt(),
                generation.getStartedAt(),
                generation.getCompletedAt()
        );
    }

    private ApiException generationNotFound(String generationId) {
        String suffix = generationId == null ? "" : " generationId=" + generationId;
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "WBS_GENERATION_NOT_FOUND",
                "WBS 생성 작업을 찾을 수 없습니다." + suffix
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
