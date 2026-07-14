package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectWbsResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectWbsResultRepository extends JpaRepository<ProjectWbsResult, Long> {
    boolean existsByAgentExecutionId(String agentExecutionId);

    boolean existsByProjectId(Long projectId);
}
