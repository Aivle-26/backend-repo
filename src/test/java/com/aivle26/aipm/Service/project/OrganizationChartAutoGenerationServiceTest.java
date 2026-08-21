package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationChartAutoGenerationServiceTest {

    private static final Long PROJECT_ID = 1L;

    @Mock private ProjectArtifactRepository artifactRepository;
    @Mock private OrganizationChartArtifactService artifactService;
    @InjectMocks private OrganizationChartAutoGenerationService service;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void confirmedWbsMissingDoesNotGenerate() {
        assertNotReady("CONFIRMED_WBS_NOT_FOUND");
    }

    @Test
    void leafScheduleMissingDoesNotGenerate() {
        assertNotReady("PLANNING_SCHEDULE_NOT_FOUND");
    }

    @Test
    void activeMemberMissingDoesNotGenerate() {
        assertNotReady("ACTIVE_PROJECT_MEMBER_NOT_FOUND");
    }

    @Test
    void allConditionsReadyGenerateInitialChartOnce() {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(false);

        assertThat(service.tryAutoGenerateOrganizationChart(PROJECT_ID)).isTrue();

        verify(artifactService).generateInitialAutomatically(PROJECT_ID);
    }

    @Test
    void existingArtifactMakesAutomaticGenerationNoOp() {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(true);

        assertThat(service.tryAutoGenerateOrganizationChart(PROJECT_ID)).isFalse();

        verify(artifactService, never()).generateInitialAutomatically(PROJECT_ID);
    }

    @Test
    void manualHierarchyVersionMakesAutomaticGenerationNoOp() {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(true);

        service.tryAutoGenerateOrganizationChart(PROJECT_ID);

        verify(artifactService, never()).generateInitialAutomatically(PROJECT_ID);
    }

    @Test
    void missingCapabilityDoesNotAddANewReadinessCondition() {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(false);

        assertThat(service.tryAutoGenerateOrganizationChart(PROJECT_ID)).isTrue();

        verify(artifactService).generateInitialAutomatically(PROJECT_ID);
    }

    @Test
    void generationFailureIsContainedAfterOriginalWriteCommits() {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(false);
        doThrow(new IllegalStateException("AI unavailable"))
                .when(artifactService).generateInitialAutomatically(PROJECT_ID);
        TransactionSynchronizationManager.initSynchronization();

        service.scheduleAfterCommit(PROJECT_ID);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

        synchronization.afterCommit();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(artifactService).generateInitialAutomatically(PROJECT_ID);
    }

    @Test
    void repeatedTriggerStopsAfterFirstArtifactExists() {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(false, true);

        assertThat(service.tryAutoGenerateOrganizationChart(PROJECT_ID)).isTrue();
        assertThat(service.tryAutoGenerateOrganizationChart(PROJECT_ID)).isFalse();

        verify(artifactService, times(1)).generateInitialAutomatically(PROJECT_ID);
    }

    private void assertNotReady(String code) {
        when(artifactRepository.existsByProjectIdAndArtifactType(
                PROJECT_ID,
                ProjectArtifactType.ORGANIZATION_CHART
        )).thenReturn(false);
        doThrow(new ApiException(HttpStatus.CONFLICT, code, "not ready"))
                .when(artifactService).generateInitialAutomatically(PROJECT_ID);

        assertThat(service.tryAutoGenerateOrganizationChart(PROJECT_ID)).isFalse();

        verify(artifactService).generateInitialAutomatically(PROJECT_ID);
    }
}
