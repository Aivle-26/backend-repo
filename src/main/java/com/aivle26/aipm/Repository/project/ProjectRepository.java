package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.Project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    // PM을 함께 로딩한 전체 프로젝트를 최근 생성 순으로 조회한다.
    @EntityGraph(attributePaths = "pm")
    List<Project> findAllByOrderByCreatedAtDesc();

    // 프로젝트 ID로 PM이 함께 로딩된 프로젝트를 조회한다.
    @EntityGraph(attributePaths = "pm")
    Optional<Project> findWithPmById(Long id);
}
