package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.AiServerProperties;
import com.aivle26.aipm.Exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.List;

@Component
public class AiServerDocumentExtractClient {
    private final RestClient aiServerRestClient;
    private final AiServerProperties properties;
    private final ObjectMapper objectMapper;

    public AiServerDocumentExtractClient(
            @Qualifier("aiServerRestClient") RestClient aiServerRestClient,
            AiServerProperties properties,
            ObjectMapper objectMapper
    ) {
        this.aiServerRestClient = aiServerRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AiServerJsonResponse extractDocuments(List<StoredDocumentFile> files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (StoredDocumentFile file : files) {
            body.add("files", toFilePart(file));
        }

        try {
            var response = aiServerRestClient.post()
                    .uri(properties.getDocumentExtractPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "AI_SERVER_INVALID_RESPONSE",
                        "AI Server가 빈 응답을 반환했습니다."
                );
            }
            return new AiServerJsonResponse(response.getStatusCode(), responseBody);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw translateAiServerError(exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI_SERVER_TIMEOUT",
                        "AI Server의 문서 처리 응답 시간이 초과되었습니다.",
                        exception
                );
            }
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_SERVER_UNAVAILABLE",
                    "AI Server에 연결할 수 없습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI_SERVER_TIMEOUT",
                        "AI Server의 문서 처리 응답 시간이 초과되었습니다.",
                        exception
                );
            }
            if (isConnectionFailure(exception)) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_SERVER_UNAVAILABLE",
                        "AI Server에 연결할 수 없습니다.",
                        exception
                );
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_SERVER_INVALID_RESPONSE",
                    "AI Server가 올바르지 않은 응답을 반환했습니다.",
                    exception
            );
        }
    }

    private HttpEntity<InputStreamResource> toFilePart(StoredDocumentFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("files", file.originalFileName());
            if (file.contentType() != null && !file.contentType().isBlank()) {
                headers.setContentType(MediaType.parseMediaType(file.contentType()));
            }
            return new HttpEntity<>(new StoredDocumentResource(file), headers);
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROJECT_DOCUMENT",
                    "저장된 파일을 읽을 수 없습니다: " + file.originalFileName(),
                    exception
            );
        }
    }

    private ApiException translateAiServerError(HttpStatusCode status, String responseBody, Exception cause) {
        String message = extractAiServerMessage(responseBody);
        if (status.value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AI_SERVER_PAYLOAD_TOO_LARGE", message, cause);
        }
        if (status.value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_SERVER_VALIDATION_FAILED", message, cause);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVER_ERROR", message, cause);
    }

    private String extractAiServerMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "AI Server 요청이 실패했습니다.";
        }
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            JsonNode detail = body.get("detail");
            if (detail == null || detail.isNull()) {
                return responseBody;
            }
            if (detail.isTextual()) {
                return detail.asText();
            }
            return detail.toString();
        } catch (Exception ignored) {
            return responseBody;
        }
    }

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

    private boolean isConnectionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return true;
            }
            if (current instanceof IOException && !isTimeout(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class StoredDocumentResource extends InputStreamResource {
        private final StoredDocumentFile file;

        private StoredDocumentResource(StoredDocumentFile file) throws IOException {
            super(Files.newInputStream(file.path()));
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
            return Files.newInputStream(file.path());
        }
    }
}
