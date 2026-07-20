package com.aivle26.aipm.Repository;

import com.aivle26.aipm.Entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
