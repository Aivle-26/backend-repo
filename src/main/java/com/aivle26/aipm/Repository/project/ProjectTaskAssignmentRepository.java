package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectTaskAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProjectTaskAssignmentRepository extends JpaRepository<ProjectTaskAssignment, Long> {
    @EntityGraph(attributePaths = {"wbsTask"})
    List<ProjectTaskAssignment> findByProjectIdOrderByWbsTask_OrderIndexAscIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"wbsTask"})
    List<ProjectTaskAssignment> findByProjectIdAndEmployeeNumberOrderByWbsTask_OrderIndexAscIdAsc(
            Long projectId,
            String employeeNumber
    );

    @EntityGraph(attributePaths = {"wbsTask"})
    Optional<ProjectTaskAssignment> findByProjectIdAndWbsTaskId(Long projectId, Long wbsTaskId);

    boolean existsByProjectIdAndEmployeeNumber(Long projectId, String employeeNumber);

    @EntityGraph(attributePaths = {"wbsTask"})
    List<ProjectTaskAssignment> findByProjectIdAndDueDateBetweenOrderByDueDateAscWbsTask_OrderIndexAsc(
            Long projectId,
            LocalDate from,
            LocalDate to
    );

    void deleteAllByProjectId(Long projectId);
}
