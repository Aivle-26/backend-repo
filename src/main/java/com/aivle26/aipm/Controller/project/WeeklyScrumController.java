package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.MissingWeeklyScrumMembersResponse;
import com.aivle26.aipm.Dto.project.SaveWeeklyScrumRequest;
import com.aivle26.aipm.Dto.project.WeeklyScrumSubmissionResponse;
import com.aivle26.aipm.Service.project.WeeklyScrumService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/weekly-scrums")
public class WeeklyScrumController {

    private final WeeklyScrumService weeklyScrumService;

    @PutMapping("/{weekStartDate}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<WeeklyScrumSubmissionResponse> save(
            @PathVariable Long projectId,
            @PathVariable LocalDate weekStartDate,
            @Valid @RequestBody SaveWeeklyScrumRequest request
    ) {
        return ResponseEntity.ok(weeklyScrumService.save(projectId, weekStartDate, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<WeeklyScrumSubmissionResponse>> getByWeek(
            @PathVariable Long projectId,
            @RequestParam LocalDate weekStartDate
    ) {
        return ResponseEntity.ok(weeklyScrumService.getByWeek(projectId, weekStartDate));
    }

    @GetMapping("/missing-members")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<MissingWeeklyScrumMembersResponse> getMissingMembers(
            @PathVariable Long projectId,
            @RequestParam LocalDate weekStartDate
    ) {
        return ResponseEntity.ok(weeklyScrumService.getMissingMembers(projectId, weekStartDate));
    }
}
