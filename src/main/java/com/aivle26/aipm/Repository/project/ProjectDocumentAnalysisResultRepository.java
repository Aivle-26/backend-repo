package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectDocumentAnalysisResult;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectDocumentAnalysisResultRepository extends JpaRepository<ProjectDocumentAnalysisResult, Long> {
    // AI 실행 ID의 분석 결과가 이미 저장되었는지 반환한다.
    boolean existsByAgentExecutionId(String agentExecutionId);

    // 프로젝트의 가장 최근 분석 결과를 생성 시각과 ID 역순으로 조회한다.
    Optional<ProjectDocumentAnalysisResult> findTopByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    // 분석 결과 ID와 프로젝트 ID가 모두 일치하는 결과를 조회한다.
    Optional<ProjectDocumentAnalysisResult> findByIdAndProjectId(Long id, Long projectId);

    // 프로젝트에 연결된 모든 문서 분석 결과를 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
