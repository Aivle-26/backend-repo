package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectDocumentAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDocumentAnalysisResultRepository extends JpaRepository<ProjectDocumentAnalysisResult, Long> {
    boolean existsByAgentExecutionId(String agentExecutionId);

    void deleteAllByProjectId(Long projectId);
}
