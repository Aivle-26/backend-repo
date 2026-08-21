package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.WeeklyScrumSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyScrumSubmissionRepository extends JpaRepository<WeeklyScrumSubmission, Long> {

    Optional<WeeklyScrumSubmission> findByProjectIdAndEmployeeNumberAndWeekStartDate(
            Long projectId,
            String employeeNumber,
            LocalDate weekStartDate
    );

    List<WeeklyScrumSubmission> findByProjectIdAndWeekStartDateOrderByUpdatedAtDescEmployeeNumberAsc(
            Long projectId,
            LocalDate weekStartDate
    );

    List<WeeklyScrumSubmission> findByProjectIdAndWeekStartDateAndEmployeeNumberOrderByUpdatedAtDesc(
            Long projectId,
            LocalDate weekStartDate,
            String employeeNumber
    );

    @Query("""
            select submission.employeeNumber
            from WeeklyScrumSubmission submission
            where submission.project.id = :projectId
              and submission.weekStartDate = :weekStartDate
            """)
    List<String> findSubmittedEmployeeNumbers(
            @Param("projectId") Long projectId,
            @Param("weekStartDate") LocalDate weekStartDate
    );

    void deleteAllByProjectId(Long projectId);
}
