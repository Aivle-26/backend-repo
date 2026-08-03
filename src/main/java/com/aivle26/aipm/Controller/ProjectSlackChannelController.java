package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.RegisterSlackChannelRequest;
import com.aivle26.aipm.Dto.SlackChannelCandidateResponse;
import com.aivle26.aipm.Dto.SlackChannelResponse;
import com.aivle26.aipm.Service.ProjectSlackChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 프로젝트에 분석 대상 Slack 채널을 연결·해제하는 API. */
@RestController
@RequestMapping("/api/projects/{projectId}/slack-channels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PM')")
public class ProjectSlackChannelController {

    private final ProjectSlackChannelService projectSlackChannelService;

    @GetMapping
    public ResponseEntity<List<SlackChannelResponse>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectSlackChannelService.list(projectId));
    }

    /** 연결 화면에서 고를 채널 후보 */
    @GetMapping("/candidates")
    public ResponseEntity<List<SlackChannelCandidateResponse>> candidates(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectSlackChannelService.listCandidates(projectId));
    }

    @PostMapping
    public ResponseEntity<SlackChannelResponse> register(
            @PathVariable Long projectId,
            @Valid @RequestBody RegisterSlackChannelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectSlackChannelService.register(projectId, request.channelId()));
    }

    @DeleteMapping("/{channelId}")
    public ResponseEntity<Void> unregister(
            @PathVariable Long projectId,
            @PathVariable String channelId) {
        projectSlackChannelService.unregister(projectId, channelId);
        return ResponseEntity.noContent().build();
    }
}
