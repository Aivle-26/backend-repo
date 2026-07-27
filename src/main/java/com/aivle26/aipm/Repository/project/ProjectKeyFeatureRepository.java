package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectKeyFeature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectKeyFeatureRepository extends JpaRepository<ProjectKeyFeature, Long> {
    // 프로젝트의 핵심 기능을 ID 오름차순으로 조회한다.
    List<ProjectKeyFeature> findByProjectIdOrderByIdAsc(Long projectId);

    // 프로젝트에 연결된 모든 핵심 기능을 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
