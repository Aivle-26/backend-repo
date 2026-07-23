package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    boolean existsByProjectId(Long projectId);

    List<ProjectDocument> findByProjectId(Long projectId);

    Optional<ProjectDocument> findByProjectIdAndOriginalFileName(Long projectId, String originalFileName);

    void deleteAllByProjectId(Long projectId);
}
