package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationChartAutoGenerationService {

    private static final Set<String> NOT_READY_CODES = Set.of(
            "CONFIRMED_WBS_NOT_FOUND",
            "PLANNING_SCHEDULE_NOT_FOUND",
            "ACTIVE_PROJECT_MEMBER_NOT_FOUND"
    );

    private final ProjectArtifactRepository artifactRepository;
    private final OrganizationChartArtifactService artifactService;
    private final ConcurrentHashMap<Long, Object> projectLocks = new ConcurrentHashMap<>();

    public void scheduleAfterCommit(Long projectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            tryAutoGenerateOrganizationChart(projectId);
                        }
                    }
            );
            return;
        }
        tryAutoGenerateOrganizationChart(projectId);
    }

    public boolean tryAutoGenerateOrganizationChart(Long projectId) {
        Object lock = projectLocks.computeIfAbsent(projectId, ignored -> new Object());
        synchronized (lock) {
            if (artifactRepository.existsByProjectIdAndArtifactType(
                    projectId,
                    ProjectArtifactType.ORGANIZATION_CHART
            )) {
                return false;
            }
            try {
                artifactService.generateInitialAutomatically(projectId);
                return true;
            } catch (ApiException exception) {
                if (NOT_READY_CODES.contains(exception.getCode())) {
                    return false;
                }
                log.warn(
                        "Automatic organization chart generation failed. projectId={}, code={}",
                        projectId,
                        exception.getCode(),
                        exception
                );
                return false;
            } catch (RuntimeException exception) {
                log.warn(
                        "Automatic organization chart generation failed. projectId={}",
                        projectId,
                        exception
                );
                return false;
            }
        }
    }
}
