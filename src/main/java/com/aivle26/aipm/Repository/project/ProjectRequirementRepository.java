package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectRequirement;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, Long> {
    // 프로젝트에 지정 상태의 요구사항이 존재하는지 반환한다.
    boolean existsByProjectIdAndStatus(Long projectId, RequirementStatus status);

    // 프로젝트에서 지정 상태와 일치하는 요구사항을 조회한다.
    List<ProjectRequirement> findByProjectIdAndStatus(Long projectId, RequirementStatus status);

    // 프로젝트와 분석 결과에 속한 요구사항을 연관 문서와 함께 ID 순으로 조회한다.
    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument"})
    List<ProjectRequirement> findByProjectIdAndAnalysisResultIdOrderByIdAsc(Long projectId, Long analysisResultId);

    // 요구사항 ID와 프로젝트 ID가 일치하는 요구사항을 연관 데이터와 함께 조회한다.
    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument"})
    Optional<ProjectRequirement> findByIdAndProjectId(Long id, Long projectId);

    // 프로젝트의 모든 요구사항을 연관 데이터와 함께 ID 오름차순으로 조회한다.
    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument"})
    List<ProjectRequirement> findByProjectIdOrderByIdAsc(Long projectId);

    // 프로젝트 요구사항을 유형·우선순위·상태·확정 조건으로 필터링해 반환한다.
    @EntityGraph(attributePaths = {"analysisResult", "sourceDocument"})
    @Query("""
            select requirement
            from ProjectRequirement requirement
            where requirement.project.id = :projectId
              and (:type is null or requirement.type = :type)
              and (:priority is null or requirement.priority = :priority)
              and (:status is null or requirement.status = :status)
              and (:confirmed is null or requirement.confirmed = :confirmed)
            order by requirement.id asc
            """)
    List<ProjectRequirement> findAllByFilters(
            @Param("projectId") Long projectId,
            @Param("type") RequirementType type,
            @Param("priority") RequirementPriority priority,
            @Param("status") RequirementStatus status,
            @Param("confirmed") Boolean confirmed
    );

    // 프로젝트에 연결된 모든 요구사항을 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
