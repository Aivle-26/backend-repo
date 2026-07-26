package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectPlanningExtraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectPlanningExtractionRepository extends JpaRepository<ProjectPlanningExtraction, Long> {
    // 프로젝트의 가장 최근 문서 추출 요약을 생성 시각과 ID 역순으로 조회한다.
    Optional<ProjectPlanningExtraction> findTopByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    // 프로젝트에 연결된 모든 문서 추출 요약을 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
