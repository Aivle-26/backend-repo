package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.CreateProjectDraftRequest;
import com.aivle26.aipm.Dto.CreateProjectDraftResponse;
import com.aivle26.aipm.Dto.ProjectDocumentUploadResponse;
import com.aivle26.aipm.Dto.AgentRequestResult;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultRequest;
import com.aivle26.aipm.Dto.SaveDocumentAnalysisResultResponse;
import com.aivle26.aipm.Dto.SaveScheduleResultRequest;
import com.aivle26.aipm.Dto.SaveScheduleResultResponse;
import com.aivle26.aipm.Dto.SaveWbsResultRequest;
import com.aivle26.aipm.Dto.SaveWbsResultResponse;
import com.aivle26.aipm.Service.ProjectDocumentAnalysisRequestService;
import com.aivle26.aipm.Service.ProjectDocumentAnalysisService;
import com.aivle26.aipm.Service.ProjectDocumentService;
import com.aivle26.aipm.Service.ProjectScheduleRequestService;
import com.aivle26.aipm.Service.ProjectScheduleService;
import com.aivle26.aipm.Service.ProjectService;
import com.aivle26.aipm.Service.ProjectWbsRequestService;
import com.aivle26.aipm.Service.ProjectWbsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectDocumentAnalysisService projectDocumentAnalysisService;
    private final ProjectDocumentAnalysisRequestService projectDocumentAnalysisRequestService;
    private final ProjectWbsService projectWbsService;
    private final ProjectWbsRequestService projectWbsRequestService;
    private final ProjectScheduleService projectScheduleService;
    private final ProjectScheduleRequestService projectScheduleRequestService;

    @PostMapping("/drafts")
    public ResponseEntity<CreateProjectDraftResponse> createProjectDraft(@Valid @RequestBody CreateProjectDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProjectDraft(request));
    }

    @PostMapping(path = "/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocumentUploadResponse> uploadDocuments(
            @PathVariable Long projectId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectDocumentService.uploadInitialDocuments(projectId, files));
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
}
