package com.aivle26.aipm.Service;

import com.aivle26.aipm.Config.PlanningAgentProperties;
import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import com.aivle26.aipm.Exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PlanningAgentHttpClient implements PlanningAgentClient {
    private final RestClient planningAgentRestClient;
    private final PlanningAgentProperties properties;

    @Override
    public PlanningDocumentExtractResponse extractDocuments(List<MultipartFile> files, boolean enableLlm) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            body.add("files", toFilePart(file));
        }
        body.add("enable_llm", String.valueOf(enableLlm));

        try {
            PlanningDocumentExtractResponse response = planningAgentRestClient.post()
                    .uri(properties.getExtractPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(PlanningDocumentExtractResponse.class);
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

    private HttpEntity<InputStreamResource> toFilePart(MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("files", file.getOriginalFilename());
            if (file.getContentType() != null) {
                headers.setContentType(MediaType.parseMediaType(file.getContentType()));
            }
            return new HttpEntity<>(new MultipartFileResource(file), headers);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_DOCUMENT", "파일을 읽을 수 없습니다: " + file.getOriginalFilename(), exception);
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

    private ApiException invalidResponse() {
        return invalidResponse(null);
    }

    private ApiException invalidResponse(Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_PLANNING_AGENT_RESPONSE", "문서 분석 결과 형식이 올바르지 않습니다.", cause);
    }

    private static final class MultipartFileResource extends InputStreamResource {
        private final MultipartFile file;

        private MultipartFileResource(MultipartFile file) throws IOException {
            super(file.getInputStream());
            this.file = file;
        }

        @Override
        public String getFilename() {
            return file.getOriginalFilename();
        }

        @Override
        public long contentLength() {
            return file.getSize();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return file.getInputStream();
        }
    }
}
