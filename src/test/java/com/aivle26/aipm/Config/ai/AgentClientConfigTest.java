package com.aivle26.aipm.Config.ai;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClientConfigTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void planningAgentRestClientSerializesJavaDatesAsIsoStrings() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        PlanningAgentProperties properties = new PlanningAgentProperties();
        properties.setBaseUrl(server.url("/").toString());
        var objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        new AgentClientConfig()
                .planningAgentRestClient(properties, objectMapper)
                .post()
                .uri("/dates")
                .body(Map.of("project_start_date", LocalDate.of(2026, 7, 7)))
                .retrieve()
                .toBodilessEntity();

        String requestBody = server.takeRequest().getBody().readUtf8();
        assertThat(requestBody).contains("\"project_start_date\":\"2026-07-07\"");
        assertThat(requestBody).doesNotContain("[2026,7,7]");
    }
}
