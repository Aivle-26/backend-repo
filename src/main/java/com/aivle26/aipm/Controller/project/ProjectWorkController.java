package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.AssignProjectTaskRequest;
import com.aivle26.aipm.Dto.project.ProjectProgressResponse;
import com.aivle26.aipm.Dto.project.ProjectProgressRateResponse;
import com.aivle26.aipm.Dto.project.ProjectMemberResponse;
import com.aivle26.aipm.Dto.project.ProjectSearchResponse;
import com.aivle26.aipm.Dto.project.SaveFinalTaskAssignmentsRequest;
import com.aivle26.aipm.Dto.project.SaveProjectMembersRequest;
import com.aivle26.aipm.Dto.project.TaskAssignmentResponse;
import com.aivle26.aipm.Dto.project.TeamProgressResponse;
import com.aivle26.aipm.Dto.project.UpdateTaskProgressRequest;
import com.aivle26.aipm.Service.project.ProjectWorkService;
import com.aivle26.aipm.Service.project.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
public class ProjectWorkController {
    private final ProjectWorkService projectWorkService;
    private final ProjectMemberService projectMemberService;

    @PutMapping("/team-members/final")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<List<ProjectMemberResponse>> replaceProjectMembers(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveProjectMembersRequest request
    ) {
        return ResponseEntity.ok(projectMemberService.replaceMembers(projectId, request));
    }

    @GetMapping("/team-members")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<ProjectMemberResponse>> getProjectMembers(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectMemberService.getMembers(projectId));
    }

    @PutMapping("/tasks/{wbsId}/assignment")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<TaskAssignmentResponse> assignTask(
            @PathVariable Long projectId,
            @PathVariable Long wbsId,
            @Valid @RequestBody AssignProjectTaskRequest request
    ) {
        return ResponseEntity.ok(projectWorkService.assignTask(projectId, wbsId, request));
    }

    @PutMapping("/assignments/final")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<List<TaskAssignmentResponse>> replaceFinalAssignments(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveFinalTaskAssignmentsRequest request
    ) {
        return ResponseEntity.ok(projectWorkService.replaceFinalAssignments(projectId, request));
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<List<TaskAssignmentResponse>> getAssignments(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectWorkService.getAssignments(projectId));
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<ProjectProgressResponse> getProjectProgress(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectWorkService.getProjectProgress(projectId));
    }

    @GetMapping("/progress-rate")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<ProjectProgressRateResponse> getProjectProgressRate(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectWorkService.getStoredProjectProgressRate(projectId));
    }

    @GetMapping("/progress/me")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<ProjectProgressResponse> getMyProgress(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectWorkService.getMyProgress(projectId));
    }

    @GetMapping("/progress/members")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<TeamProgressResponse> getTeamProgress(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectWorkService.getTeamProgress(projectId));
    }

    @GetMapping("/tasks/me")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<TaskAssignmentResponse>> getMyTasks(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectWorkService.getMyTasks(projectId));
    }

    @GetMapping("/tasks/due-soon")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<TaskAssignmentResponse>> getDueSoonTasks(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "3") int days
    ) {
        return ResponseEntity.ok(projectWorkService.getDueSoonTasks(projectId, days));
    }

    @PatchMapping("/tasks/{wbsId}/progress")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<TaskAssignmentResponse> updateTaskProgress(
            @PathVariable Long projectId,
            @PathVariable Long wbsId,
            @Valid @RequestBody UpdateTaskProgressRequest request
    ) {
        return ResponseEntity.ok(projectWorkService.updateProgress(projectId, wbsId, request));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<ProjectSearchResponse> search(
            @PathVariable Long projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(projectWorkService.search(projectId, query, limit));
    }
}
