package com.aivle26.aipm.Dto;

import java.util.List;

/**
 * 프론트로 내보내는 "산출물 보안 검사" 응답.
 *
 * <p>AI 서버 응답을 camelCase로 통과시킨다.
 * registrationAllowed=false면 위험도가 높아 등록을 막아야 한다.
 */
public record SecurityCheckResponse(
        Long projectId,
        String artifactName,
        int securityRiskScore,
        String securityRiskLevel,
        boolean registrationAllowed,
        List<Detection> detections,
        String maskedContent,
        List<String> recommendations
) {

    public record Detection(
            String detectionType,
            int count,
            String description
    ) {
        static Detection from(AiSecurityCheckResponse.AiDetection ai) {
            return new Detection(ai.detectionType(), ai.count(), ai.description());
        }
    }

    /** AI 서버 응답(snake_case DTO) → 프론트 응답(camelCase). */
    public static SecurityCheckResponse from(AiSecurityCheckResponse ai) {
        List<Detection> detections = ai.detections() == null
                ? List.of()
                : ai.detections().stream().map(Detection::from).toList();

        return new SecurityCheckResponse(
                ai.projectId(),
                ai.artifactName(),
                ai.securityRiskScore(),
                ai.securityRiskLevel(),
                ai.registrationAllowed(),
                detections,
                ai.maskedContent(),
                ai.recommendations() == null ? List.of() : ai.recommendations());
    }
}
