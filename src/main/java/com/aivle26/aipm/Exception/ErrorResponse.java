package com.aivle26.aipm.Exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String error,
        String message
) {
}
