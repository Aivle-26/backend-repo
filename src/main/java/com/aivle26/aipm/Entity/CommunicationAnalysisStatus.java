package com.aivle26.aipm.Entity;

/** 프로젝트별 커뮤니케이션 리스크 분석 이력 상태. */
public enum CommunicationAnalysisStatus {
    COMPLETED,
    PENDING,
    NEVER_ANALYZED,

    /**
     * 분석은 정상 수행됐지만 분석 창(최근 14일) 안에 메시지가 한 건도 없어 판정할 게 없었음.
     *
     * <p>NEVER_ANALYZED와 반드시 구분해야 한다. 둘을 합치면 프론트가 "분석 시작" 버튼을
     * 다시 띄우는데, 눌러도 같은 결과라 사용자에겐 고장으로 보인다.
     */
    NO_RECENT_MESSAGES
}
