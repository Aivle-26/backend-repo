package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectScheduleResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectScheduleResultRepository extends JpaRepository<ProjectScheduleResult, Long> {
    boolean existsByAgentExecutionId(String agentExecutionId);

    boolean existsByProjectId(Long projectId);
}
