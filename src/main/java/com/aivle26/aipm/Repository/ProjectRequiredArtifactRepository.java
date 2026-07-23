package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectRequiredArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRequiredArtifactRepository extends JpaRepository<ProjectRequiredArtifact, Long> {
    List<ProjectRequiredArtifact> findByProjectIdOrderByIdAsc(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
