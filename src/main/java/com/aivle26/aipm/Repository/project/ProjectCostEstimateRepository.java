package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectCostEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectCostEstimateRepository extends JpaRepository<ProjectCostEstimate, Long> {
    Optional<ProjectCostEstimate> findByProjectId(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
