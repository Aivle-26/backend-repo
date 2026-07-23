package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    boolean existsByProjectId(Long projectId);

    List<ProjectDocument> findByProjectId(Long projectId);

    List<ProjectDocument> findByProjectIdOrderByCreatedAtAscIdAsc(Long projectId);

    @Query("""
            select document
            from ProjectDocument document
            join fetch document.project project
            order by project.id asc, document.createdAt asc, document.id asc
            """)
    List<ProjectDocument> findAllWithProjectOrderByProjectIdAndCreatedAt();

    Optional<ProjectDocument> findByProjectIdAndOriginalFileName(Long projectId, String originalFileName);

    void deleteAllByProjectId(Long projectId);
}
