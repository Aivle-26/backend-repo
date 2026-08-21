package com.aivle26.aipm.Entity;

/**
 * AI 서버가 LLM을 실제로 썼는지 나타내는 상태.
 * OPENAI_API_KEY가 없어도 규칙 기반으로 정상 판정되므로 SKIPPED_NO_API_KEY는 오류가 아니다.
 */
public enum CommunicationLlmStatus {
    SUCCEEDED,
    SKIPPED_NO_API_KEY,
    FALLBACK,
    DISABLED
}
