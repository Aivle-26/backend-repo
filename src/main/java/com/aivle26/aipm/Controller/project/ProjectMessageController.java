package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.CreateFeedbackRequest;
import com.aivle26.aipm.Dto.project.CreateNoticeRequest;
import com.aivle26.aipm.Dto.project.CreateScrumRequestsRequest;
import com.aivle26.aipm.Dto.project.ProjectMessageResponse;
import com.aivle26.aipm.Service.project.ProjectMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
public class ProjectMessageController {

    private final ProjectMessageService messageService;

    @PostMapping("/notices")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectMessageResponse> createNotice(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateNoticeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.createNotice(projectId, request));
    }

    @GetMapping("/notices")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<ProjectMessageResponse>> getNotices(@PathVariable Long projectId) {
        return ResponseEntity.ok(messageService.getNotices(projectId));
    }

    @PostMapping("/feedbacks")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectMessageResponse> createFeedback(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateFeedbackRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.createFeedback(projectId, request));
    }

    @GetMapping("/feedbacks")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<ProjectMessageResponse>> getFeedbacks(@PathVariable Long projectId) {
        return ResponseEntity.ok(messageService.getFeedbacks(projectId));
    }

    @PostMapping("/scrum-requests")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<List<ProjectMessageResponse>> createScrumRequests(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateScrumRequestsRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.createScrumRequests(projectId, request));
    }

    @GetMapping("/scrum-requests")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<ProjectMessageResponse>> getScrumRequests(@PathVariable Long projectId) {
        return ResponseEntity.ok(messageService.getScrumRequests(projectId));
    }
}
