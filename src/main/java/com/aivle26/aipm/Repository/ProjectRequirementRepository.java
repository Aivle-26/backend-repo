package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectRequirement;
import com.aivle26.aipm.Entity.RequirementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, Long> {
    boolean existsByProjectIdAndStatus(Long projectId, RequirementStatus status);

    List<ProjectRequirement> findByProjectIdAndStatus(Long projectId, RequirementStatus status);

    void deleteAllByProjectId(Long projectId);
}
