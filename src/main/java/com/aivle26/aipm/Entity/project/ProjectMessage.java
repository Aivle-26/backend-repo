package com.aivle26.aipm.Entity.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "project_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scrum_request_project_recipient_week",
                columnNames = {"project_id", "message_type", "recipient_employee_number", "target_week_start"}
        )
)
public class ProjectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private ProjectMessageType type;

    @Column(nullable = false, length = 50)
    private String senderEmployeeNumber;

    @Column(name = "recipient_employee_number", length = 50)
    private String recipientEmployeeNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "target_week_start")
    private LocalDate targetWeekStart;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ProjectMessage(Project project, ProjectMessageType type, String senderEmployeeNumber,
                           String recipientEmployeeNumber, String title, String content,
                           LocalDate targetWeekStart) {
        this.project = project;
        this.type = type;
        this.senderEmployeeNumber = senderEmployeeNumber;
        this.recipientEmployeeNumber = recipientEmployeeNumber;
        this.title = title;
        this.content = content;
        this.targetWeekStart = targetWeekStart;
    }

    public static ProjectMessage notice(Project project, String sender, String title, String content) {
        return new ProjectMessage(project, ProjectMessageType.NOTICE, sender, null, title, content, null);
    }

    public static ProjectMessage feedback(Project project, String sender, String recipient,
                                          String title, String content) {
        return new ProjectMessage(project, ProjectMessageType.FEEDBACK, sender, recipient, title, content, null);
    }

    public static ProjectMessage scrumRequest(Project project, String sender, String recipient,
                                              LocalDate weekStart, String content) {
        return new ProjectMessage(project, ProjectMessageType.SCRUM_REQUEST, sender, recipient,
                "주간 스크럼 작성 요청", content, weekStart);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
