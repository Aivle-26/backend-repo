package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.ProjectAssistantQueryRequest;
import com.aivle26.aipm.Dto.project.ProjectAssistantQueryResponse;
import com.aivle26.aipm.Service.project.ProjectAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/assistant")
public class ProjectAssistantController {

    private final ProjectAssistantService projectAssistantService;

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<ProjectAssistantQueryResponse> query(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectAssistantQueryRequest request
    ) {
        return ResponseEntity.ok(projectAssistantService.query(projectId, request));
    }
}
