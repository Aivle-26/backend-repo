package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectWbsTask;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectWbsTaskRepository extends JpaRepository<ProjectWbsTask, Long> {
    // 프로젝트에 확정된 WBS 작업이 존재하는지 반환한다.
    boolean existsByProjectIdAndConfirmedTrue(Long projectId);

    // 프로젝트에 확정된 모든 WBS 작업을 조회한다.
    List<ProjectWbsTask> findByProjectIdAndConfirmedTrue(Long projectId);

    // 요구사항이 연결된 WBS 작업 수를 조회해 삭제 가능 여부 판단에 사용한다.
    @Query("select count(task) from ProjectWbsTask task join task.requirements requirement where requirement.id = :requirementId")
    long countByRequirementId(@Param("requirementId") Long requirementId);

    // 프로젝트 WBS 작업의 부모 참조를 모두 해제한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProjectWbsTask task set task.parentTask = null where task.project.id = :projectId")
    void clearParentTasksByProjectId(@Param("projectId") Long projectId);

    // 프로젝트 WBS 작업과 요구사항 간 연결 행을 모두 삭제한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from project_wbs_task_requirements where wbs_task_id in (select id from project_wbs_tasks where project_id = :projectId)", nativeQuery = true)
    void deleteRequirementLinksByProjectId(@Param("projectId") Long projectId);

    // 프로젝트 WBS 작업과 기술 간 연결 행을 모두 삭제한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from project_wbs_task_skills where wbs_task_id in (select id from project_wbs_tasks where project_id = :projectId)", nativeQuery = true)
    void deleteSkillLinksByProjectId(@Param("projectId") Long projectId);

    // 프로젝트에 연결된 모든 WBS 작업을 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
