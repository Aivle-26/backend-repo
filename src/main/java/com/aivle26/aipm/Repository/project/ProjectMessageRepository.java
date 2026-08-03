package com.aivle26.aipm.Repository.project;

import com.aivle26.aipm.Entity.project.ProjectMessage;
import com.aivle26.aipm.Entity.project.ProjectMessageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProjectMessageRepository extends JpaRepository<ProjectMessage, Long> {

    List<ProjectMessage> findByProjectIdAndTypeOrderByCreatedAtDescIdDesc(
            Long projectId,
            ProjectMessageType type
    );

    List<ProjectMessage> findByProjectIdAndTypeAndRecipientEmployeeNumberOrderByCreatedAtDescIdDesc(
            Long projectId,
            ProjectMessageType type,
            String recipientEmployeeNumber
    );

    boolean existsByProjectIdAndTypeAndRecipientEmployeeNumberAndTargetWeekStart(
            Long projectId,
            ProjectMessageType type,
            String recipientEmployeeNumber,
            LocalDate targetWeekStart
    );

    void deleteAllByProjectId(Long projectId);
}
