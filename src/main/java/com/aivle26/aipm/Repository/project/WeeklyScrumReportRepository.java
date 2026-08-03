package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.WeeklyScrumReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyScrumReportRepository extends JpaRepository<WeeklyScrumReport, Long> {
    @EntityGraph(attributePaths = "project")
    Optional<WeeklyScrumReport> findByProjectIdAndWeekStartDate(
            Long projectId,
            LocalDate weekStartDate
    );

    void deleteAllByProjectId(Long projectId);
}
