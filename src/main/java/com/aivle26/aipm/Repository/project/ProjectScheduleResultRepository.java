package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectScheduleResult;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectScheduleResultRepository extends JpaRepository<ProjectScheduleResult, Long> {
    // AI 실행 ID의 일정 결과가 이미 저장되었는지 반환한다.
    boolean existsByAgentExecutionId(String agentExecutionId);

    // 프로젝트에 저장된 일정 생성 결과가 존재하는지 반환한다.
    boolean existsByProjectId(Long projectId);

    // 프로젝트에 연결된 모든 일정 생성 결과를 삭제한다.
    void deleteAllByProjectId(Long projectId);
}
