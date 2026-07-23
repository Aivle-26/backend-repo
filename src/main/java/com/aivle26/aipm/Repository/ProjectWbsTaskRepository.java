package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectWbsTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectWbsTaskRepository extends JpaRepository<ProjectWbsTask, Long> {
    boolean existsByProjectIdAndConfirmedTrue(Long projectId);

    List<ProjectWbsTask> findByProjectIdAndConfirmedTrue(Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectWbsTask task set task.parentTask = null where task.project.id = :projectId")
    void clearParentTasksByProjectId(@Param("projectId") Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from project_wbs_task_requirements where wbs_task_id in (select id from project_wbs_tasks where project_id = :projectId)", nativeQuery = true)
    void deleteRequirementLinksByProjectId(@Param("projectId") Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from project_wbs_task_skills where wbs_task_id in (select id from project_wbs_tasks where project_id = :projectId)", nativeQuery = true)
    void deleteSkillLinksByProjectId(@Param("projectId") Long projectId);

    void deleteAllByProjectId(Long projectId);
}
