package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectPlanningExtraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectPlanningExtractionRepository extends JpaRepository<ProjectPlanningExtraction, Long> {
    Optional<ProjectPlanningExtraction> findTopByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
