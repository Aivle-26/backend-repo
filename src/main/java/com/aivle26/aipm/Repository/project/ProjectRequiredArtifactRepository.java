package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectRequiredArtifact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRequiredArtifactRepository extends JpaRepository<ProjectRequiredArtifact, Long> {
    // 프로젝트의 필수 산출물을 ID 오름차순으로 조회한다.
    List<ProjectRequiredArtifact> findByProjectIdOrderByIdAsc(Long projectId);

    // 프로젝트에 연결된 모든 필수 산출물을 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
