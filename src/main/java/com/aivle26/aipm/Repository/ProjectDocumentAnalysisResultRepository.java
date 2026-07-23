package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectDocumentAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectDocumentAnalysisResultRepository extends JpaRepository<ProjectDocumentAnalysisResult, Long> {
    boolean existsByAgentExecutionId(String agentExecutionId);

    Optional<ProjectDocumentAnalysisResult> findTopByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
