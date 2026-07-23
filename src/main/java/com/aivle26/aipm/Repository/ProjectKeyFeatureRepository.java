package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectKeyFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectKeyFeatureRepository extends JpaRepository<ProjectKeyFeature, Long> {
    List<ProjectKeyFeature> findByProjectIdOrderByIdAsc(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
