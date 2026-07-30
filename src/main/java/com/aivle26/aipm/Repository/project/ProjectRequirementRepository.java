package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, Long> {

    boolean existsByProjectId(Long projectId);

    @Query("""
            select requirement
            from ProjectRequirement requirement
            where requirement.project.id = :projectId
              and requirement.status = :status
              and requirement.includedInFinal = true
            order by requirement.id asc
            """)
    List<ProjectRequirement> findByProjectIdAndStatus(
            @Param("projectId") Long projectId,
            @Param("status") RequirementStatus status
    );

    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument", "evidences", "evidences.document"})
    List<ProjectRequirement> findByProjectIdAndAiSuggestionJsonIsNotNullOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument", "evidences", "evidences.document"})
    List<ProjectRequirement> findByProjectIdAndAnalysisResultIdOrderByIdAsc(Long projectId, Long analysisResultId);

    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument", "evidences", "evidences.document"})
    Optional<ProjectRequirement> findByIdAndProjectId(Long id, Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select requirement
            from ProjectRequirement requirement
            where requirement.id = :requirementId
              and requirement.project.id = :projectId
            """)
    Optional<ProjectRequirement> findForUpdate(
            @Param("requirementId") Long requirementId,
            @Param("projectId") Long projectId
    );

    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument", "evidences", "evidences.document"})
    List<ProjectRequirement> findByProjectIdOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument", "evidences", "evidences.document"})
    @Query("""
            select requirement
            from ProjectRequirement requirement
            where requirement.project.id = :projectId
              and (:type is null or requirement.type = :type)
              and (:priority is null or requirement.priority = :priority)
              and (:status is null or requirement.status = :status)
              and (:confirmed is null or requirement.confirmed = :confirmed)
              and requirement.includedInFinal = true
            order by requirement.id asc
            """)
    List<ProjectRequirement> findAllByFilters(
            @Param("projectId") Long projectId,
            @Param("type") RequirementType type,
            @Param("priority") RequirementPriority priority,
            @Param("status") RequirementStatus status,
            @Param("confirmed") Boolean confirmed
    );

    void deleteAllByProjectId(Long projectId);
}
