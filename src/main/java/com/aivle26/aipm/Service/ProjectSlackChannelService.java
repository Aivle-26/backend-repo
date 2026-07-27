package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.SlackChannelCandidateResponse;
import com.aivle26.aipm.Dto.SlackChannelResponse;
import com.aivle26.aipm.Entity.project.Project;
import com.aivle26.aipm.Entity.ProjectSlackChannel;
import com.aivle26.aipm.Exception.ApiException;
import com.aivle26.aipm.Repository.project.ProjectRepository;
import com.aivle26.aipm.Repository.ProjectSlackChannelRepository;
import com.slack.api.model.Conversation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 프로젝트 ↔ Slack 채널 매핑 관리. */
@Service
@RequiredArgsConstructor
public class ProjectSlackChannelService {

    private final SlackClient slackClient;
    private final ProjectRepository projectRepository;
    private final ProjectSlackChannelRepository projectSlackChannelRepository;

    @Transactional(readOnly = true)
    public List<SlackChannelResponse> list(Long projectId) {
        requireProject(projectId);
        return projectSlackChannelRepository.findByProjectId(projectId).stream()
                .map(SlackChannelResponse::from)
                .toList();
    }

    /** 워크스페이스 채널 목록에 연결 여부를 표시해 돌려준다. */
    @Transactional(readOnly = true)
    public List<SlackChannelCandidateResponse> listCandidates(Long projectId) {
        requireProject(projectId);

        Set<String> linked = projectSlackChannelRepository.findByProjectId(projectId).stream()
                .map(ProjectSlackChannel::getChannelId)
                .collect(Collectors.toSet());

        return slackClient.listChannels().stream()
                .map(c -> new SlackChannelCandidateResponse(
                        c.getId(),
                        c.getName(),
                        c.isPrivate(),
                        c.isMember(),
                        linked.contains(c.getId())))
                .toList();
    }

    /**
     * 채널을 프로젝트에 연결한다.
     *
     * <p>채널 이름은 클라이언트가 보낸 값을 믿지 않고 Slack에서 직접 조회한다.
     * 없는 채널 ID가 등록되면 한참 뒤 수집 단계에서야 실패해 원인 찾기가 어려워진다.
     */
    @Transactional
    public SlackChannelResponse register(Long projectId, String channelId) {
        Project project = requireProject(projectId);

        projectSlackChannelRepository.findByProjectIdAndChannelId(projectId, channelId)
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "CHANNEL_ALREADY_LINKED",
                            "이미 연결된 채널입니다.");
                });

        Conversation conversation = slackClient.listChannels().stream()
                .filter(c -> channelId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLACK_CHANNEL_NOT_FOUND",
                        "워크스페이스에서 채널을 찾을 수 없습니다."));

        if (!conversation.isMember()) {
            // 봇이 없으면 conversations.history가 not_in_channel로 실패한다.
            // 수집 시점이 아니라 등록 시점에 알려주는 편이 원인을 찾기 쉽다.
            throw new ApiException(HttpStatus.CONFLICT, "SLACK_BOT_NOT_IN_CHANNEL",
                    "봇이 이 채널에 참여하지 않았습니다. Slack에서 /invite 로 봇을 초대한 뒤 다시 시도하세요.");
        }

        ProjectSlackChannel entity = new ProjectSlackChannel();
        entity.setProject(project);
        entity.setChannelId(conversation.getId());
        entity.setChannelName(conversation.getName());

        return SlackChannelResponse.from(projectSlackChannelRepository.save(entity));
    }

    /**
     * 연결 해제. 수집된 메시지는 지우지 않는다.
     * 과거 분석 결과의 근거 메시지가 사라지면 그 결과를 해석할 수 없게 된다.
     */
    @Transactional
    public void unregister(Long projectId, String channelId) {
        requireProject(projectId);
        ProjectSlackChannel channel = projectSlackChannelRepository
                .findByProjectIdAndChannelId(projectId, channelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHANNEL_NOT_LINKED",
                        "연결되지 않은 채널입니다."));
        projectSlackChannelRepository.delete(channel);
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "프로젝트를 찾을 수 없습니다."));
    }
}
