package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.AgentRequestResult;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationRequest;
import com.aivle26.aipm.Dto.project.AssignmentRecommendationResponse;
import com.aivle26.aipm.Dto.project.CostEstimateRequest;
import com.aivle26.aipm.Dto.project.CostEstimateResponse;
import com.aivle26.aipm.Dto.project.FinalCostEstimateResponse;
import com.aivle26.aipm.Dto.project.CreateProjectDraftFromDocumentsResponse;
import com.aivle26.aipm.Dto.project.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.project.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.project.ProjectDocumentAnalysisResultsResponse;
import com.aivle26.aipm.Dto.project.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Dto.project.ProjectScheduleResponse;
import com.aivle26.aipm.Dto.project.ProjectSummaryResponse;
import com.aivle26.aipm.Dto.project.ProjectWbsResponse;
import com.aivle26.aipm.Dto.project.SaveFinalWbsRequest;
import com.aivle26.aipm.Dto.project.SaveFinalScheduleRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.project.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Dto.project.SaveScheduleResultRequest;
import com.aivle26.aipm.Dto.project.SaveScheduleResultResponse;
import com.aivle26.aipm.Dto.project.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.project.SaveWbsResultResponse;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Dto.ProjectArtifactStatusResponse;
import com.aivle26.aipm.Service.ProjectArtifactStatusService;
import com.aivle26.aipm.Service.auth.AuthenticatedUser;
import com.aivle26.aipm.Service.project.ProjectCreationService;
import com.aivle26.aipm.Service.project.AssignmentRecommendationService;
import com.aivle26.aipm.Service.project.CostEstimateService;
import com.aivle26.aipm.Service.project.FinalCostEstimateService;
import com.aivle26.aipm.Service.project.ProjectDocumentAnalysisService;
import com.aivle26.aipm.Service.project.ProjectDocumentExtractService;
import com.aivle26.aipm.Service.project.ProjectDocumentService;
import com.aivle26.aipm.Service.project.ProjectScheduleService;
import com.aivle26.aipm.Service.project.ProjectService;
import com.aivle26.aipm.Service.project.ProjectWbsService;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectArtifactStatusService projectArtifactStatusService;
    private final ProjectCreationService projectCreationService;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectDocumentExtractService projectDocumentExtractService;
    private final ProjectDocumentAnalysisService projectDocumentAnalysisService;
    private final ProjectWbsService projectWbsService;
    private final ProjectScheduleService projectScheduleService;
    private final AssignmentRecommendationService assignmentRecommendationService;
    private final CostEstimateService costEstimateService;
    private final FinalCostEstimateService finalCostEstimateService;

    // 저장된 프로젝트를 조회해 화면용 요약 목록으로 반환한다.
    @GetMapping
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<List<ProjectSummaryResponse>> listProjects(Authentication authentication) {
        return ResponseEntity.ok(projectService.listProjects(requireAuthenticatedUser(authentication)));
    }

    // 접근 가능한 한 프로젝트의 저장 문서 메타데이터만 반환한다.
    @GetMapping("/{projectId}/documents")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<ProjectDocumentUploadResponse> listProjectDocuments(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectDocumentService.listProjectDocuments(projectId));
    }

    @GetMapping("/{projectId}/documents/{documentId}/content")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<byte[]> getProjectDocumentContent(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    ) {
        ProjectDocumentService.ProjectDocumentContent content =
                projectDocumentService.getPdfContent(projectId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(
                                        content.originalFileName(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(content.content());
    }

    // 인증된 PM의 프로젝트와 연결 문서 및 저장 파일을 함께 삭제한다.
    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId, Authentication authentication) {
        AuthenticatedUser user = requireAuthenticatedUser(authentication);
        projectService.deleteProject(projectId, user.employeeNumber());
        return ResponseEntity.noContent().build();
    }

    // 입력된 프로젝트 정보로 문서가 없는 초안 프로젝트를 생성해 반환한다.
    @PostMapping("/drafts")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<CreateProjectDraftResponse> createProjectDraft(@Valid @RequestBody CreateProjectDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectCreationService.createProjectDraft(request));
    }

    // 업로드 문서를 저장한 뒤 AI 추출 결과로 프로젝트 초안을 생성해 반환한다.
    @PostMapping(path = "/drafts/from-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<CreateProjectDraftFromDocumentsResponse> createProjectDraftFromDocuments(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "enableLlm", defaultValue = "true") boolean enableLlm,
            @RequestParam(value = "pmUserId", required = false) String pmUserId,
            Authentication authentication
    ) {
        String pmEmployeeNumber = resolvePmEmployeeNumber(authentication, pmUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectCreationService.createDraftFromDocuments(files, enableLlm, pmEmployeeNumber));
    }

    // 프로젝트 식별자와 업로드 파일을 받아 검증·저장된 문서 정보를 반환한다.
    @PostMapping(path = "/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectDocumentUploadResponse> uploadDocuments(
            @PathVariable Long projectId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectDocumentService.uploadInitialDocuments(projectId, files));
    }

    // 프로젝트에 저장된 실제 파일을 AI 추출 서버에 전달하고 원본 응답을 반환한다.
    @PostMapping("/{projectId}/documents/extract")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<JsonNode> extractDocuments(@PathVariable Long projectId) {
        var response = projectDocumentExtractService.extractStoredDocuments(projectId);
        return ResponseEntity.status(response.status()).body(response.body());
    }

    // 프로젝트·문서·최신 분석 데이터를 한 번에 조회해 화면용 DTO로 반환한다.
    @GetMapping("/{projectId}/documents/analysis-results")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectDocumentAnalysisResultsResponse> getDocumentAnalysisResults(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectDocumentAnalysisService.getAnalysisResults(projectId));
    }

    // AI Server가 전달한 프로젝트 문서 분석 결과를 검증·저장하고 식별자를 반환한다.
    @PostMapping("/{projectId}/documents/analysis-results")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<SaveDocumentAnalysisResultResponse> saveDocumentAnalysisResult(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveDocumentAnalysisResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectDocumentAnalysisService.saveAnalysisResult(projectId, request));
    }

    // 프로젝트의 확정 요구사항을 AI Server에 전달하고 생성된 WBS를 즉시 저장한다.
    @PostMapping("/{projectId}/wbs/generate")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectWbsResponse> generateWbs(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectWbsService.generateWbs(projectId));
    }

    // AI Server가 전달한 WBS 결과를 프로젝트에 저장하고 저장 결과를 반환한다.
    @PostMapping("/{projectId}/wbs/results")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<SaveWbsResultResponse> saveWbsResult(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveWbsResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectWbsService.saveWbsResult(projectId, request));
    }

    // 새로고침 시 AI 최초안과 사용자의 현재 최종안을 DB에서 함께 복원한다.
    @GetMapping("/{projectId}/wbs")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectWbsResponse> getWbs(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectWbsService.getWbs(projectId));
    }

    // 편집된 전체 최종 WBS를 교체 저장해 추가·수정·삭제·순서 변경을 원자적으로 반영한다.
    @PutMapping("/{projectId}/wbs/final")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectWbsResponse> saveFinalWbs(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveFinalWbsRequest request
    ) {
        return ResponseEntity.ok(projectWbsService.saveFinalWbs(projectId, request));
    }

    // 프로젝트의 저장 WBS를 기반으로 AI 일정 생성을 요청한다.
    @PostMapping("/{projectId}/schedules/generate")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<AgentRequestResult> requestScheduleGeneration(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(projectScheduleService.requestScheduleGeneration(projectId));
    }

    // AI Server가 전달한 일정 결과를 검증·저장하고 저장 결과를 반환한다.
    @PostMapping("/{projectId}/schedules/results")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<SaveScheduleResultResponse> saveScheduleResult(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveScheduleResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectScheduleService.saveScheduleResult(projectId, request));
    }

    // 저장된 WBS별 P50·P80·P90 일정과 선행 관계를 모두 반환한다.
    @GetMapping("/{projectId}/schedules")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectScheduleResponse> getSchedules(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(projectScheduleService.getSchedules(projectId));
    }

    @PutMapping("/{projectId}/schedules/final")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectScheduleResponse> saveFinalSchedule(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveFinalScheduleRequest request
    ) {
        return ResponseEntity.ok(projectScheduleService.saveFinalSchedule(projectId, request));
    }

    @PostMapping("/{projectId}/assignments/recommend")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<AssignmentRecommendationResponse> recommendAssignments(
            @PathVariable Long projectId,
            @Valid @RequestBody AssignmentRecommendationRequest request
    ) {
        return ResponseEntity.ok(
                assignmentRecommendationService.recommend(projectId, request)
        );
    }

    @PostMapping("/{projectId}/costs/estimate")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<CostEstimateResponse> estimateProjectCost(
            @PathVariable Long projectId,
            @Valid @RequestBody CostEstimateRequest request
    ) {
        return ResponseEntity.ok(costEstimateService.estimate(projectId, request));
    }

    @PutMapping("/{projectId}/costs/final")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<FinalCostEstimateResponse> saveFinalCostEstimate(
            @PathVariable Long projectId,
            @Valid @RequestBody CostEstimateRequest request
    ) {
        return ResponseEntity.ok(finalCostEstimateService.saveFinal(projectId, request));
    }

    @GetMapping("/{projectId}/artifacts/status")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<ProjectArtifactStatusResponse> getArtifactStatus(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectArtifactStatusService.getStatus(projectId));
    }

    // 인증 정보 또는 요청값에서 프로젝트 담당자 사번을 결정해 반환한다.
    private String resolvePmEmployeeNumber(Authentication authentication, String pmUserId) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.employeeNumber();
        }
        if (pmUserId != null && !pmUserId.isBlank()) {
            return pmUserId.trim();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PM_USER_REQUIRED", "PM 사용자를 확인할 수 없습니다.");
    }

    // 인증 주체를 현재 사용자로 검증하고 인증 사용자 정보를 반환한다.
    private AuthenticatedUser requireAuthenticatedUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다.");
    }
}
