package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectArtifactRepository extends JpaRepository<ProjectArtifact, Long> {
    List<ProjectArtifact> findByProjectId(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
