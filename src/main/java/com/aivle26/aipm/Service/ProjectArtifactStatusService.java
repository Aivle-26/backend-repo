package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.ArtifactRegisterItemResponse;
import com.aivle26.aipm.Dto.ProjectArtifactStatusResponse;
import com.aivle26.aipm.Entity.ArtifactApprovalStatus;
import com.aivle26.aipm.Entity.ArtifactCheckStatus;
import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.ProjectArtifactType;
import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;
import com.aivle26.aipm.Repository.ProjectArtifactRepository;
import com.aivle26.aipm.Repository.project.ProjectRequiredArtifactRepository;
import com.aivle26.aipm.Service.project.OrganizationChartArtifactPolicy;
import com.aivle26.aipm.Service.project.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectArtifactStatusService {
    private final ProjectRequiredArtifactRepository requiredArtifactRepository;
    private final ProjectArtifactRepository artifactRepository;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ArtifactVersionComparator versionComparator;
    private final OrganizationChartArtifactPolicy organizationChartArtifactPolicy;

    @Transactional
    public ProjectArtifactStatusResponse getStatus(Long projectId) {
        projectAuthorizationService.requireProjectPm(projectId);
        organizationChartArtifactPolicy.ensureForProject(projectId);

        List<ProjectRequiredArtifact> requiredArtifacts =
                requiredArtifactRepository.findByProjectIdOrderByArtifactTypeAsc(projectId);
        Map<ProjectArtifactType, ProjectArtifact> currentArtifacts = selectCurrentArtifacts(
                artifactRepository.findByProjectId(projectId)
        );

        List<ArtifactRegisterItemResponse> artifactRegister = new ArrayList<>();
        List<ArtifactRegisterItemResponse> missingArtifacts = new ArrayList<>();
        List<ArtifactRegisterItemResponse> unapprovedArtifacts = new ArrayList<>();
        List<ArtifactRegisterItemResponse> outdatedArtifacts = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int registeredCount = 0;
        int approvedCount = 0;

        for (ProjectRequiredArtifact requiredArtifact : requiredArtifacts) {
            ProjectArtifact artifact = currentArtifacts.get(requiredArtifact.getArtifactType());
            ArtifactRegisterItemResponse item;
            if (artifact == null) {
                item = missingItem(requiredArtifact);
                missingArtifacts.add(item);
                recommendations.add("Register required artifact: " + requiredArtifact.getArtifactName() + ".");
            } else {
                registeredCount++;
                boolean versionSatisfied = versionComparator.compare(
                        artifact.getVersion(),
                        requiredArtifact.getRequiredVersion()
                ) >= 0;
                boolean approvalSatisfied = artifact.getApprovalStatus() == ArtifactApprovalStatus.APPROVED;
                ArtifactCheckStatus status = versionSatisfied && approvalSatisfied
                        ? ArtifactCheckStatus.COMPLETED
                        : ArtifactCheckStatus.INCOMPLETE;
                item = registeredItem(requiredArtifact, artifact, status);

                if (!versionSatisfied) {
                    outdatedArtifacts.add(item);
                    recommendations.add("Update " + requiredArtifact.getArtifactName()
                            + " to version " + requiredArtifact.getRequiredVersion() + " or later.");
                }
                if (!approvalSatisfied) {
                    unapprovedArtifacts.add(item);
                    recommendations.add("Complete approval for " + requiredArtifact.getArtifactName() + ".");
                }
                if (status == ArtifactCheckStatus.COMPLETED) {
                    approvedCount++;
                }
            }
            artifactRegister.add(item);
        }

        int totalRequiredCount = requiredArtifacts.size();
        if (totalRequiredCount == 0) {
            recommendations.add("No required artifacts are configured for this project.");
        } else if (approvedCount == totalRequiredCount) {
            recommendations.add("All required artifacts are registered and approved.");
        }

        return new ProjectArtifactStatusResponse(
                projectId,
                totalRequiredCount,
                registeredCount,
                approvedCount,
                rate(registeredCount, totalRequiredCount),
                rate(approvedCount, totalRequiredCount),
                List.copyOf(artifactRegister),
                List.copyOf(missingArtifacts),
                List.copyOf(unapprovedArtifacts),
                List.copyOf(outdatedArtifacts),
                List.copyOf(recommendations)
        );
    }

    private Map<ProjectArtifactType, ProjectArtifact> selectCurrentArtifacts(List<ProjectArtifact> artifacts) {
        Map<ProjectArtifactType, ProjectArtifact> currentArtifacts = new EnumMap<>(ProjectArtifactType.class);
        for (ProjectArtifact artifact : artifacts) {
            currentArtifacts.merge(artifact.getArtifactType(), artifact, this::latestArtifact);
        }
        return currentArtifacts;
    }

    private ProjectArtifact latestArtifact(ProjectArtifact current, ProjectArtifact candidate) {
        int versionComparison = versionComparator.compare(candidate.getVersion(), current.getVersion());
        if (versionComparison != 0) {
            return versionComparison > 0 ? candidate : current;
        }

        LocalDateTime currentUpdatedAt = current.getUpdatedAt() == null
                ? LocalDateTime.MIN
                : current.getUpdatedAt();
        LocalDateTime candidateUpdatedAt = candidate.getUpdatedAt() == null
                ? LocalDateTime.MIN
                : candidate.getUpdatedAt();
        int updatedAtComparison = candidateUpdatedAt.compareTo(currentUpdatedAt);
        if (updatedAtComparison != 0) {
            return updatedAtComparison > 0 ? candidate : current;
        }

        long currentId = current.getId() == null ? Long.MIN_VALUE : current.getId();
        long candidateId = candidate.getId() == null ? Long.MIN_VALUE : candidate.getId();
        return candidateId > currentId ? candidate : current;
    }

    private ArtifactRegisterItemResponse missingItem(ProjectRequiredArtifact requiredArtifact) {
        return new ArtifactRegisterItemResponse(
                requiredArtifact.getArtifactType(),
                requiredArtifact.getArtifactName(),
                ArtifactCheckStatus.MISSING,
                null,
                requiredArtifact.getRequiredVersion(),
                null
        );
    }

    private ArtifactRegisterItemResponse registeredItem(
            ProjectRequiredArtifact requiredArtifact,
            ProjectArtifact artifact,
            ArtifactCheckStatus status
    ) {
        return new ArtifactRegisterItemResponse(
                requiredArtifact.getArtifactType(),
                artifact.getArtifactName(),
                status,
                artifact.getVersion(),
                requiredArtifact.getRequiredVersion(),
                artifact.getApprovalStatus()
        );
    }

    private double rate(int count, int total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
