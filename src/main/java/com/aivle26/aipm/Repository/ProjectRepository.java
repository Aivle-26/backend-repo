package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = "pm")
    List<Project> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "pm")
    Optional<Project> findWithPmById(Long id);
}
