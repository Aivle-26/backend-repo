package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.ProjectSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectScheduleRepository extends JpaRepository<ProjectSchedule, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from project_schedule_predecessors
            where schedule_id in (select id from project_schedules where project_id = :projectId)
               or predecessor_schedule_id in (select id from project_schedules where project_id = :projectId)
            """, nativeQuery = true)
    void deletePredecessorLinksByProjectId(@Param("projectId") Long projectId);

    void deleteAllByProjectId(Long projectId);
}
