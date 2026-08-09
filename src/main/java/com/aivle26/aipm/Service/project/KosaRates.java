package com.aivle26.aipm.Service.project;

import com.aivle26.aipm.Exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

final class KosaRates {
    static final int RATE_YEAR = 2026;
    private static final Map<String, Long> MONTHLY_RATES = Map.ofEntries(
            Map.entry("IT 기획자", 11_853_218L), Map.entry("IT 컨설턴트", 10_707_960L),
            Map.entry("업무분석가", 9_740_667L), Map.entry("데이터분석가", 8_499_309L),
            Map.entry("IT PM", 10_086_804L), Map.entry("IT 아키텍트", 11_103_230L),
            Map.entry("UI/UX 기획·개발자", 6_901_660L), Map.entry("UI/UX 디자이너", 5_159_246L),
            Map.entry("응용 SW 개발자", 7_754_124L), Map.entry("시스템 SW 개발자", 5_840_196L),
            Map.entry("정보시스템운용자", 10_649_117L), Map.entry("IT 지원기술자", 5_170_016L),
            Map.entry("IT 마케터", 11_793_500L), Map.entry("IT 품질관리자", 11_042_071L),
            Map.entry("IT 테스터", 4_053_137L), Map.entry("IT 감리", 11_745_154L),
            Map.entry("정보보안전문가", 10_411_680L)
    );

    private KosaRates() {
    }

    static boolean supports(String category) {
        return category != null && MONTHLY_RATES.containsKey(category);
    }

    static long monthlyRate(String category) {
        Long rate = MONTHLY_RATES.get(category);
        if (rate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_KOSA_JOB_CATEGORY",
                    "지원하지 않는 KOSA 직무입니다. kosaJobCategory=" + category);
        }
        return rate;
    }
}
