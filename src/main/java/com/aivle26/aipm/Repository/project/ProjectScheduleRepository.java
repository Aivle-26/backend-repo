package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectScheduleRepository extends JpaRepository<ProjectSchedule, Long> {
    // 프로젝트 일정 삭제 전에 선행 일정 연결 테이블의 관련 행을 제거한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from project_schedule_predecessors
            where schedule_id in (select id from project_schedules where project_id = :projectId)
               or predecessor_schedule_id in (select id from project_schedules where project_id = :projectId)
            """, nativeQuery = true)
    void deletePredecessorLinksByProjectId(@Param("projectId") Long projectId);

    // 프로젝트에 연결된 모든 일정 레코드를 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
