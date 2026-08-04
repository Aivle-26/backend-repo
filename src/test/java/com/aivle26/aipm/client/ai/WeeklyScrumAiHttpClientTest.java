package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeeklyScrumAiHttpClientTest {
    private MockRestServiceServer server;
    private WeeklyScrumAiHttpClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WeeklyScrumAiHttpClient(
                builder.build(),
                new PlanningAgentProperties(),
                objectMapper
        );
    }

    @Test
    void acceptsFallbackAsSuccessfulResponseAndSendsRequestId() throws Exception {
        server.expect(once(), requestTo(
                        "http://localhost:8000/api/v1/reports/weekly-scrum/summarize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Request-ID", org.hamcrest.Matchers.notNullValue()))
                .andRespond(withSuccess(
                        "{\"llm_status\":\"FALLBACK\",\"fact_summary\":{},\"team_summary\":{}}",
                        MediaType.APPLICATION_JSON
                ));

        var result = client.summarize(objectMapper.readTree("{\"project_id\":101}"));

        assertThat(result.body().path("llm_status").asText()).isEqualTo("FALLBACK");
        assertThat(result.requestId()).isNotBlank();
        server.verify();
    }

    @Test
    void mapsAiValidationErrorToUnprocessableEntity() throws Exception {
        server.expect(once(), requestTo(
                        "http://localhost:8000/api/v1/reports/weekly-scrum/review"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"VALIDATION_ERROR\",\"request_id\":\"ai-request\"}"));

        assertThatThrownBy(() -> client.review(objectMapper.readTree("{}")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo("WEEKLY_SCRUM_AI_INVALID_REQUEST");
                });
        server.verify();
    }
}
