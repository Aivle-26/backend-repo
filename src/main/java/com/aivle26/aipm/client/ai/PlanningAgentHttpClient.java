package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Config.ai.PlanningAgentProperties;
import com.aivle26.aipm.Dto.project.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Exception.ApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlanningAgentHttpClient implements PlanningAgentClient {
    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;
    private final ObjectMapper objectMapper;

    // 저장 파일과 LLM 사용 여부를 분석 서버에 전송해 구조화된 추출 응답으로 반환한다.
    @Override
    public PlanningDocumentExtractResponse extractDocuments(List<StoredDocumentFile> files, boolean enableLlm) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (StoredDocumentFile file : files) {
            body.add("files", toFilePart(file));
        }
        body.add("enable_llm", String.valueOf(enableLlm));

        try {
            ResponseEntity<byte[]> responseEntity = planningAgentRestClient.post()
                    .uri(properties.getExtractPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);
            PlanningDocumentExtractResponse response = decodeResponse(responseEntity.getBody());
            if (response == null) {
                throw invalidResponse();
            }
            return response;
        } catch (HttpClientErrorException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PLANNING_AGENT_CLIENT_ERROR", "문서 분석 서버가 요청을 처리할 수 없습니다.", exception);
        } catch (HttpServerErrorException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PLANNING_AGENT_SERVER_ERROR", "문서 분석 서버에서 오류가 발생했습니다.", exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PLANNING_AGENT_TIMEOUT", "문서 분석 처리 시간이 초과되었습니다.", exception);
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLANNING_AGENT_UNAVAILABLE", "문서 분석 서버에 연결할 수 없습니다.", exception);
        } catch (RestClientException exception) {
            throw invalidResponse(exception);
        }
    }

    // UTF-8 응답 바이트를 문서 추출 DTO로 역직렬화하며 빈 본문은 null로 반환한다.
    private PlanningDocumentExtractResponse decodeResponse(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, PlanningDocumentExtractResponse.class);
        } catch (IOException exception) {
            throw invalidResponse(exception);
        }
    }

    // 저장 파일의 원본명과 MIME 유형을 보존한 multipart 파일 항목을 생성한다.
    private HttpEntity<InputStreamResource> toFilePart(StoredDocumentFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("files", file.originalFileName());
        if (file.contentType() != null && !file.contentType().isBlank()) {
            headers.setContentType(MediaType.parseMediaType(file.contentType()));
        }
        return new HttpEntity<>(new StoredDocumentResource(file), headers);
    }

    // 예외 원인 체인에 소켓 시간 초과가 포함되는지 검사해 반환한다.
    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // 원인 예외가 없는 잘못된 분석 서버 응답용 ApiException을 생성한다.
    private ApiException invalidResponse() {
        return invalidResponse(null);
    }

    // 원인 예외를 포함한 잘못된 분석 서버 응답용 ApiException을 생성한다.
    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PLANNING_AGENT_RESPONSE", "문서 분석 결과 형식이 올바르지 않습니다.", cause);
    }

    private static final class StoredDocumentResource extends InputStreamResource {
        private final StoredDocumentFile file;

        private StoredDocumentResource(StoredDocumentFile file) {
            super(new ByteArrayInputStream(file.content()));
            this.file = file;
        }

        @Override
        public String getFilename() {
            return file.originalFileName();
        }

        @Override
        public long contentLength() {
            return file.fileSize();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(file.content());
        }
    }
}
