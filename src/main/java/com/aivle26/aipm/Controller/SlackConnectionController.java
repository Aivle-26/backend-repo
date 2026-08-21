package com.aivle26.aipm.Controller;

import com.aivle26.aipm.Dto.SlackConnectionResponse;
import com.aivle26.aipm.Service.SlackClient;
import com.slack.api.model.Conversation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Slack 연결 점검용.
 *
 * <p>봇 토큰이 유효한지, 어느 채널을 읽을 수 있는지를 가장 먼저 확인하기 위한 엔드포인트다.
 * 이게 되면 그다음 단계(채널 연결 → 메시지 수집 → AI 분석)에서 원인 찾기가 훨씬 쉬워진다.
 */
@RestController
@RequestMapping("/api/slack")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PM')")
public class SlackConnectionController {

    private final SlackClient slackClient;

    @GetMapping("/connection")
    public ResponseEntity<SlackConnectionResponse> connection() {
        List<Conversation> channels = slackClient.listChannels();

        List<SlackConnectionResponse.Channel> joined = new ArrayList<>();
        List<SlackConnectionResponse.Channel> notJoined = new ArrayList<>();
        for (Conversation c : channels) {
            SlackConnectionResponse.Channel item =
                    new SlackConnectionResponse.Channel(c.getId(), c.getName(), c.isPrivate());
            if (c.isMember()) {
                joined.add(item);
            } else {
                notJoined.add(item);
            }
        }
        return ResponseEntity.ok(new SlackConnectionResponse(
                true, channels.size(), joined, notJoined));
    }
}
