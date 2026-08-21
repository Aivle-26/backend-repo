package com.aivle26.aipm.Config.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({AiServerProperties.class, PlanningAgentProperties.class})
public class AgentClientConfig {
    // AI 추출 서버 주소와 연결·응답 제한 시간을 적용한 RestClient를 생성한다.
    @Bean
    RestClient aiServerRestClient(AiServerProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getResponseTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> useApplicationObjectMapper(converters, objectMapper))
                .build();
    }

    // 프로젝트 분석 서버 주소와 타임아웃을 적용한 Planning Agent RestClient를 생성한다.
    @Bean
    RestClient planningAgentRestClient(PlanningAgentProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> useApplicationObjectMapper(converters, objectMapper))
                .build();
    }

    // RestClient's standalone default mapper writes Java time values as arrays. FastAPI expects ISO-8601 strings.
    private void useApplicationObjectMapper(
            java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters,
            ObjectMapper objectMapper
    ) {
        int jsonConverterIndex = 0;
        while (jsonConverterIndex < converters.size()
                && !(converters.get(jsonConverterIndex) instanceof MappingJackson2HttpMessageConverter)) {
            jsonConverterIndex++;
        }
        converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        converters.add(Math.min(jsonConverterIndex, converters.size()), new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
