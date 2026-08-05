package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectArtifact;
import com.aivle26.aipm.Entity.ProjectArtifactType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectArtifactRepository extends JpaRepository<ProjectArtifact, Long> {
    List<ProjectArtifact> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"project", "document", "document.project"})
    List<ProjectArtifact> findByProjectIdAndArtifactType(
            Long projectId,
            ProjectArtifactType artifactType
    );

    void deleteAllByProjectId(Long projectId);
}
