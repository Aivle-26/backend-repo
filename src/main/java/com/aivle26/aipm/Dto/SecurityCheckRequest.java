package com.aivle26.aipm.Dto;

/**
 * 프론트가 보내는 "산출물 보안 검사" 요청 body.
 *
 * <p>projectId와 deliverableId는 경로 변수라 여기 없다.
 * 검사할 산출물의 이름·타입·본문 텍스트를 담아 백엔드가 AI 서버로 넘긴다.
 */
public record SecurityCheckRequest(
        String artifactName,
        String artifactType,
        String textContent
) {
}
