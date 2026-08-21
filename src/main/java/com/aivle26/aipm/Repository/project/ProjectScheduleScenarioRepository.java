package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectScheduleScenario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectScheduleScenarioRepository extends JpaRepository<ProjectScheduleScenario, Long> {
    void deleteAllByProjectSchedule_Project_Id(Long projectId);
}
