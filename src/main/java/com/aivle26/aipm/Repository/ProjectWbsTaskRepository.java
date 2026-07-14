package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectWbsTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectWbsTaskRepository extends JpaRepository<ProjectWbsTask, Long> {
    boolean existsByProjectIdAndConfirmedTrue(Long projectId);

    List<ProjectWbsTask> findByProjectIdAndConfirmedTrue(Long projectId);
}
