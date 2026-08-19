package com.aivle26.aipm.Exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException exception) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                "AUTH_FORBIDDEN",
                status.getReasonPhrase(),
                "Access is denied"
        ));
    }

    // 도메인 ApiException의 상태·코드·메시지를 공통 오류 응답으로 반환한다.
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                exception.getCode(),
                status.getReasonPhrase(),
                exception.getMessage()
        ));
    }

    // DTO 필드 검증 오류를 필드별 메시지로 조합해 400 응답으로 반환한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        return buildBadRequest(message.isBlank() ? "invalid request" : message);
    }

    // JSON·경로 변수·multipart 해석 실패를 공통 잘못된 요청 응답으로 반환한다.
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MultipartException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildBadRequest("invalid request");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                "RESOURCE_NOT_FOUND",
                status.getReasonPhrase(),
                "resource not found"
        ));
    }

    /**
     * 처리되지 않은 예외의 내부 정보를 숨기고 공통 500 응답을 반환한다.
     *
     * <p>응답 본문은 "internal server error"로 고정이라 클라이언트만 봐서는 원인을 알 수 없다.
     * 그래서 스택트레이스를 반드시 여기서 남긴다. 로깅이 없으면 운영 500을 코드 정독으로
     * 추측해야 하고, 로컬(H2 + ddl-auto=create-drop)에서 재현되지 않는 종류의 장애는
     * 사실상 진단이 불가능해진다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                null,
                status.getReasonPhrase(),
                "internal server error"
        ));
    }

    // 전달된 검증 메시지로 표준 400 오류 응답을 생성한다.
    private ResponseEntity<ErrorResponse> buildBadRequest(String message) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                null,
                status.getReasonPhrase(),
                message
        ));
    }

    // 검증 실패 필드명과 기본 메시지를 클라이언트 표시 문자열로 결합한다.
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
