package com.aivle26.aipm.Controller.project;

import com.aivle26.aipm.Dto.project.UiMockupArtifactResponse;
import com.aivle26.aipm.Service.project.UiMockupArtifactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/artifacts/ui-mockup")
public class UiMockupArtifactController {

    private final UiMockupArtifactService uiMockupArtifactService;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('PM')")
    public ResponseEntity<UiMockupArtifactResponse> generate(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(uiMockupArtifactService.generate(projectId));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<UiMockupArtifactResponse> getLatest(@PathVariable Long projectId) {
        return ResponseEntity.ok(uiMockupArtifactService.getLatest(projectId));
    }

    @GetMapping("/latest/download")
    @PreAuthorize("hasAnyRole('PM', 'STAFF')")
    public ResponseEntity<byte[]> downloadLatest(@PathVariable Long projectId) {
        UiMockupArtifactService.UiMockupContent content = uiMockupArtifactService.downloadLatest(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(content.content().length)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(content.content());
    }
}
