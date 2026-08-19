package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectWbsGeneration;
import com.aivle26.aipm.Entity.project.WbsGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectWbsGenerationRepository extends JpaRepository<ProjectWbsGeneration, String> {
    Optional<ProjectWbsGeneration> findFirstByProjectIdAndStatusOrderByCreatedAtDesc(
            Long projectId,
            WbsGenerationStatus status
    );

    Optional<ProjectWbsGeneration> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectWbsGeneration> findByIdAndProjectId(String id, Long projectId);

    List<ProjectWbsGeneration> findAllByStatusOrderByCreatedAtAsc(WbsGenerationStatus status);

    void deleteAllByProjectId(Long projectId);
}
