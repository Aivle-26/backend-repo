package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.CreateProjectDraftFromDocumentsResponse;
import com.aivle26.aipm.Dto.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Dto.ProjectSummaryResponse;
import com.aivle26.aipm.Dto.AgentRequestResult;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Dto.SaveScheduleResultRequest;
import com.aivle26.aipm.Dto.SaveScheduleResultResponse;
import com.aivle26.aipm.Dto.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.SaveWbsResultResponse;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Service.AuthenticatedUser;
import com.aivle26.aipm.Service.ProjectDocumentAnalysisRequestService;
import com.aivle26.aipm.Service.ProjectDocumentAnalysisService;
import com.aivle26.aipm.Service.ProjectDocumentExtractService;
import com.aivle26.aipm.Service.ProjectDocumentService;
import com.aivle26.aipm.Service.ProjectDraftFromDocumentsService;
import com.aivle26.aipm.Service.ProjectScheduleRequestService;
import com.aivle26.aipm.Service.ProjectScheduleService;
import com.aivle26.aipm.Service.ProjectService;
import com.aivle26.aipm.Service.ProjectWbsRequestService;
import com.aivle26.aipm.Service.ProjectWbsService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectDraftFromDocumentsService projectDraftFromDocumentsService;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectDocumentExtractService projectDocumentExtractService;
    private final ProjectDocumentAnalysisService projectDocumentAnalysisService;
    private final ProjectDocumentAnalysisRequestService projectDocumentAnalysisRequestService;
    private final ProjectWbsService projectWbsService;
    private final ProjectWbsRequestService projectWbsRequestService;
    private final ProjectScheduleService projectScheduleService;
    private final ProjectScheduleRequestService projectScheduleRequestService;

    @GetMapping
    public ResponseEntity<List<ProjectSummaryResponse>> listProjects() {
        return ResponseEntity.ok(projectService.listProjects());
    }

    @PostMapping("/drafts")
    public ResponseEntity<CreateProjectDraftResponse> createProjectDraft(@Valid @RequestBody CreateProjectDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProjectDraft(request));
    }

    @PostMapping(path = "/drafts/from-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateProjectDraftFromDocumentsResponse> createProjectDraftFromDocuments(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "enableLlm", defaultValue = "true") boolean enableLlm,
            @RequestParam(value = "pmUserId", required = false) String pmUserId,
            Authentication authentication
    ) {
        String pmEmployeeNumber = resolvePmEmployeeNumber(authentication, pmUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectDraftFromDocumentsService.createDraftFromDocuments(files, enableLlm, pmEmployeeNumber));
    }

    @PostMapping(path = "/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocumentUploadResponse> uploadDocuments(
            @PathVariable Long projectId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectDocumentService.uploadInitialDocuments(projectId, files));
    }

    @PostMapping(path = "/{projectId}/documents/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JsonNode> extractDocuments(
            @PathVariable Long projectId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        var response = projectDocumentExtractService.extractDocuments(projectId, files);
        return ResponseEntity.status(response.status()).body(response.body());
    }

    @PostMapping("/{projectId}/documents/analyze")
    public ResponseEntity<AgentRequestResult> requestDocumentAnalysis(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(projectDocumentAnalysisRequestService.requestAnalysis(projectId));
    }

    @PostMapping("/{projectId}/documents/analysis-results")
    public ResponseEntity<SaveDocumentAnalysisResultResponse> saveDocumentAnalysisResult(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveDocumentAnalysisResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectDocumentAnalysisService.saveAnalysisResult(projectId, request));
    }

    @PostMapping("/{projectId}/wbs/generate")
    public ResponseEntity<AgentRequestResult> requestWbsGeneration(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(projectWbsRequestService.requestWbsGeneration(projectId));
    }

    @PostMapping("/{projectId}/wbs/results")
    public ResponseEntity<SaveWbsResultResponse> saveWbsResult(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveWbsResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectWbsService.saveWbsResult(projectId, request));
    }

    @PostMapping("/{projectId}/schedules/generate")
    public ResponseEntity<AgentRequestResult> requestScheduleGeneration(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(projectScheduleRequestService.requestScheduleGeneration(projectId));
    }

    @PostMapping("/{projectId}/schedules/results")
    public ResponseEntity<SaveScheduleResultResponse> saveScheduleResult(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveScheduleResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectScheduleService.saveScheduleResult(projectId, request));
    }

    private String resolvePmEmployeeNumber(Authentication authentication, String pmUserId) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.employeeNumber();
        }
        // TODO: 인증 연동이 완전히 정리되면 pmUserId fallback 제거
        if (pmUserId != null && !pmUserId.isBlank()) {
            return pmUserId.trim();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PM_USER_REQUIRED", "PM 사용자를 확인할 수 없습니다.");
    }
}
