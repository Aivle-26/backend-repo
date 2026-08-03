package com.aivle26.aipm.Dto.project;

import com.aivle26.aipm.Entity.project.ProjectMessage;
import com.aivle26.aipm.Entity.project.ProjectMessageType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectMessageResponse(
        Long id,
        Long projectId,
        ProjectMessageType type,
        String senderEmployeeNumber,
        String recipientEmployeeNumber,
        String title,
        String content,
        LocalDate targetWeekStart,
        LocalDateTime createdAt
) {
    public static ProjectMessageResponse from(ProjectMessage message) {
        return new ProjectMessageResponse(
                message.getId(),
                message.getProject().getId(),
                message.getType(),
                message.getSenderEmployeeNumber(),
                message.getRecipientEmployeeNumber(),
                message.getTitle(),
                message.getContent(),
                message.getTargetWeekStart(),
                message.getCreatedAt()
        );
    }
}
