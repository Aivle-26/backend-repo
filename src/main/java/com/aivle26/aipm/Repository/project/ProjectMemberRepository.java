package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProjectIdOrderByUser_NameAscUser_EmployeeNumberAsc(Long projectId);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProjectIdAndActiveTrueOrderByUser_NameAscUser_EmployeeNumberAsc(Long projectId);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProjectIdAndUser_EmployeeNumberIn(
            Long projectId,
            Collection<String> employeeNumbers
    );

    @EntityGraph(attributePaths = "user")
    Optional<ProjectMember> findByProjectIdAndUser_EmployeeNumberAndActiveTrue(
            Long projectId,
            String employeeNumber
    );

    boolean existsByProjectIdAndUser_EmployeeNumberAndActiveTrue(
            Long projectId,
            String employeeNumber
    );

    @Query("""
            select member.project.id
            from ProjectMember member
            where member.user.employeeNumber = :employeeNumber
              and member.active = true
            """)
    List<Long> findActiveProjectIds(@Param("employeeNumber") String employeeNumber);

    void deleteAllByProjectId(Long projectId);
}
