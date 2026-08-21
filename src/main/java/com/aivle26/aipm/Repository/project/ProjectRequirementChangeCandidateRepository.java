package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectRequirementChangeCandidate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRequirementChangeCandidateRepository
        extends JpaRepository<ProjectRequirementChangeCandidate, Long> {

    @EntityGraph(attributePaths = {"existingRequirement"})
    List<ProjectRequirementChangeCandidate> findByProjectIdOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"existingRequirement"})
    Optional<ProjectRequirementChangeCandidate> findByIdAndProjectId(
            Long id,
            Long projectId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select candidate
            from ProjectRequirementChangeCandidate candidate
            left join fetch candidate.existingRequirement
            where candidate.project.id = :projectId
              and candidate.id in :candidateIds
            order by candidate.id asc
            """)
    List<ProjectRequirementChangeCandidate> findForApply(
            @Param("projectId") Long projectId,
            @Param("candidateIds") List<Long> candidateIds
    );

    void deleteAllByProjectId(Long projectId);
}
