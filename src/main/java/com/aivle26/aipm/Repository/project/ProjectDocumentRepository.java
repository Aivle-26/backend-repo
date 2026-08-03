package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    // 프로젝트에 연결된 문서 레코드가 존재하는지 반환한다.
    boolean existsByProjectId(Long projectId);

    // 프로젝트에 연결된 모든 문서 레코드를 조회한다.
    List<ProjectDocument> findByProjectId(Long projectId);

    // 프로젝트 문서를 생성 시각과 ID 오름차순으로 조회한다.
    List<ProjectDocument> findByProjectIdOrderByCreatedAtAscIdAsc(Long projectId);

    // 프로젝트에서 원본 파일명이 일치하는 교체 대상 문서를 조회한다.
    Optional<ProjectDocument> findByProjectIdAndOriginalFileName(Long projectId, String originalFileName);

    // 문서 ID와 프로젝트 ID가 모두 일치하는 문서를 조회한다.
    Optional<ProjectDocument> findByIdAndProjectId(Long id, Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select document
            from ProjectDocument document
            where document.id = :documentId
              and document.project.id = :projectId
            """)
    Optional<ProjectDocument> findForUpdate(
            @Param("projectId") Long projectId,
            @Param("documentId") Long documentId
    );

    List<ProjectDocument> findByProjectIdAndIdInOrderByIdAsc(
            Long projectId,
            List<Long> documentIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select document
            from ProjectDocument document
            where document.project.id = :projectId
              and document.id in :documentIds
            order by document.id asc
            """)
    List<ProjectDocument> findForUpdate(
            @Param("projectId") Long projectId,
            @Param("documentIds") List<Long> documentIds
    );

    // 프로젝트에 연결된 모든 문서 레코드를 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
