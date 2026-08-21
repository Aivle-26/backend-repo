package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.AnalyzeProjectRequirementsRequest;
import com.aivle26.aipm.Dto.project.ApplyRequirementChangesRequest;
import com.aivle26.aipm.Dto.project.CreateProjectRequirementRequest;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Dto.project.ProjectRequirementsResponse;
import com.aivle26.aipm.Dto.project.RequirementChangeCandidateResponse;
import com.aivle26.aipm.Dto.project.RequirementReadjustmentResponse;
import com.aivle26.aipm.Dto.project.ReviewRequirementChangeRequest;
import com.aivle26.aipm.Dto.project.SaveFinalRequirementsRequest;
import com.aivle26.aipm.Dto.project.UpdateProjectRequirementRequest;
import com.aivle26.aipm.Entity.project.RequirementPriority;
import com.aivle26.aipm.Entity.project.RequirementStatus;
import com.aivle26.aipm.Entity.project.RequirementType;
import com.aivle26.aipm.Service.project.ProjectDocumentAnalysisService;
import com.aivle26.aipm.Service.project.ProjectRequirementService;
import com.aivle26.aipm.Service.project.ProjectRequirementReadjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/requirements")
@PreAuthorize("hasRole('PM')")
public class ProjectRequirementController {
    private final ProjectRequirementService projectRequirementService;
    private final ProjectDocumentAnalysisService projectDocumentAnalysisService;
    private final ProjectRequirementReadjustmentService readjustmentService;

    // Analyzes the selected project documents and returns the persisted requirements.
    @PostMapping("/analyze")
    public ResponseEntity<ProjectRequirementsResponse> analyze(
            @PathVariable Long projectId,
            @Valid @RequestBody AnalyzeProjectRequirementsRequest request
    ) {
        return ResponseEntity.ok(
                projectDocumentAnalysisService.analyzeRequirements(
                        projectId,
                        request.documentIds(),
                        request.forceRequested()
                )
        );
    }

    @PostMapping("/readjust")
    public ResponseEntity<RequirementReadjustmentResponse> readjust(
            @PathVariable Long projectId,
            @Valid @RequestBody AnalyzeProjectRequirementsRequest request
    ) {
        return ResponseEntity.ok(
                readjustmentService.createCandidates(
                        projectId,
                        request.documentIds()
                )
        );
    }

    @GetMapping("/readjustments")
    public ResponseEntity<RequirementReadjustmentResponse> listReadjustments(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(readjustmentService.listCandidates(projectId));
    }

    @PutMapping("/readjustments/{candidateId}")
    public ResponseEntity<RequirementChangeCandidateResponse> reviewReadjustment(
            @PathVariable Long projectId,
            @PathVariable Long candidateId,
            @Valid @RequestBody ReviewRequirementChangeRequest request
    ) {
        return ResponseEntity.ok(
                readjustmentService.review(projectId, candidateId, request)
        );
    }

    @PostMapping("/readjustments/apply")
    public ResponseEntity<ProjectRequirementsResponse> applyReadjustments(
            @PathVariable Long projectId,
            @Valid @RequestBody ApplyRequirementChangesRequest request
    ) {
        return ResponseEntity.ok(
                readjustmentService.apply(projectId, request.candidateIds())
        );
    }

    // 프로젝트와 원본 문서를 검증해 미확정 요구사항을 생성하고 반환한다.
    @PostMapping
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse.RequirementDetail> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectRequirementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectRequirementService.create(projectId, request));
    }

    // 프로젝트 요구사항을 선택 조건으로 필터링해 목록으로 반환한다.
    @GetMapping
    public ResponseEntity<ProjectRequirementsResponse> findAll(
            @PathVariable Long projectId,
            @RequestParam(required = false) RequirementType type,
            @RequestParam(required = false) RequirementPriority priority,
            @RequestParam(required = false) RequirementStatus status,
            @RequestParam(required = false) Boolean confirmed
    ) {
        return ResponseEntity.ok(projectRequirementService.findAll(projectId, type, priority, status, confirmed));
    }

    // 화면 오른쪽의 전체 요구사항 편집본을 한 번에 저장한다.
    @PutMapping("/final")
    public ResponseEntity<ProjectRequirementsResponse> saveFinal(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveFinalRequirementsRequest request
    ) {
        return ResponseEntity.ok(projectRequirementService.saveFinal(projectId, request));
    }

    // 프로젝트에 속한 요구사항 한 건을 검증해 상세 정보로 반환한다.
    @GetMapping("/{requirementId}")
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse.RequirementDetail> findOne(
            @PathVariable Long projectId,
            @PathVariable Long requirementId
    ) {
        return ResponseEntity.ok(projectRequirementService.findOne(projectId, requirementId));
    }

    // 프로젝트 요구사항의 전달된 필드만 수정하고 갱신 결과를 반환한다.
    @PatchMapping("/{requirementId}")
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse.RequirementDetail> update(
            @PathVariable Long projectId,
            @PathVariable Long requirementId,
            @Valid @RequestBody UpdateProjectRequirementRequest request
    ) {
        return ResponseEntity.ok(projectRequirementService.update(projectId, requirementId, request));
    }

    // 미확정이며 WBS에 연결되지 않은 프로젝트 요구사항을 삭제한다.
    @DeleteMapping("/{requirementId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long requirementId) {
        projectRequirementService.delete(projectId, requirementId);
        return ResponseEntity.noContent().build();
    }

    // 프로젝트 요구사항을 확정 상태로 변경하고 갱신 결과를 반환한다.
    @PatchMapping("/{requirementId}/confirm")
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse.RequirementDetail> confirm(
            @PathVariable Long projectId,
            @PathVariable Long requirementId
    ) {
        return ResponseEntity.ok(projectRequirementService.confirm(projectId, requirementId));
    }

    // 프로젝트 요구사항의 확정을 취소하고 미확정 결과를 반환한다.
    @PatchMapping("/{requirementId}/unconfirm")
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse.RequirementDetail> unconfirm(
            @PathVariable Long projectId,
            @PathVariable Long requirementId
    ) {
        return ResponseEntity.ok(projectRequirementService.unconfirm(projectId, requirementId));
    }

    // 프로젝트 요구사항을 반려 상태로 변경하고 갱신 결과를 반환한다.
    @PatchMapping("/{requirementId}/reject")
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse.RequirementDetail> reject(
            @PathVariable Long projectId,
            @PathVariable Long requirementId
    ) {
        return ResponseEntity.ok(projectRequirementService.reject(projectId, requirementId));
    }

    // 프로젝트의 모든 요구사항을 확정하고 갱신된 목록을 반환한다.
    @PatchMapping("/confirm")
    public ResponseEntity<List<ProjectDocumentAnalysisResultsResponse.RequirementDetail>> confirmAll(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectRequirementService.confirmAll(projectId));
    }
}
