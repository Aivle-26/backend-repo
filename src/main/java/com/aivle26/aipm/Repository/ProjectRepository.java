package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = "pm")
    List<Project> findAllByOrderByCreatedAtDesc();

    boolean existsByIdAndPm_EmployeeNumber(Long id, String employeeNumber);
}
