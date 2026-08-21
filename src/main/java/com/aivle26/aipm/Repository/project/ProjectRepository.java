package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.Project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    boolean existsByIdAndPm_EmployeeNumber(Long id, String employeeNumber);

    // PM이 소유한 프로젝트를 최근 생성 순으로 조회한다.
    @EntityGraph(attributePaths = "pm")
    List<Project> findAllByPm_EmployeeNumberOrderByCreatedAtDesc(String employeeNumber);

    // 전달된 프로젝트 ID만 PM과 함께 최근 생성 순으로 조회한다.
    @EntityGraph(attributePaths = "pm")
    List<Project> findAllByIdInOrderByCreatedAtDesc(List<Long> projectIds);

    // 프로젝트 ID로 PM이 함께 로딩된 프로젝트를 조회한다.
    @EntityGraph(attributePaths = "pm")
    Optional<Project> findWithPmById(Long id);

    @EntityGraph(attributePaths = "pm")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :projectId")
    Optional<Project> findForUpdate(@Param("projectId") Long projectId);
}
