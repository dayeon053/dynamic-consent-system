package com.dynamicconsent.model;

/**
 * 위험도 5단계 등급 분류
 *
 * 점수 범위: 최솟값 3.0점 ~ 최댓값 45.5점
 *   - 최솟값: DS=1 + (ES=1 × TF=1 × PC=1.0 × AI=1.0) × 2 = 3.0
 *   - 최댓값: DS=5 + (ES=3 × TF=3 × PC=1.5 × AI=1.5) × 2 = 45.5
 *
 * 등급 구간 근거:
 * 1) PIA 공식 구간(3~14점)의 상대 비율을 3~45.5점으로 확장
 * 2) NIST SP 800-30 표준 5단계 백분위 구간 적용
 * 3) ISO 27005 5단계 위험 등급과 1:1 대응
 */
public enum RiskGrade {

    /**
     * Very Low Risk — 매우 안전
     * 점수 구간: 3.0 이상 ~ 7.0 미만
     * NIST 백분위: 0~9%  |  ISO 27005: Negligible
     * 권장 조치: 연 1회 점검, 현재 상태 유지
     */
    VERY_LOW(3.0, 7.0, "Very Low Risk", "매우 안전", "연 1회 점검, 현재 상태 유지"),

    /**
     * Low Risk — 안전
     * 점수 구간: 7.0 이상 ~ 14.0 미만
     * NIST 백분위: 10~49%  |  ISO 27005: Minor
     * 권장 조치: 분기별 점검, 기본 보안 조치
     */
    LOW(7.0, 14.0, "Low Risk", "안전", "분기별 점검, 기본 보안 조치"),

    /**
     * Medium Risk — 보통
     * 점수 구간: 14.0 이상 ~ 24.0 미만
     * NIST 백분위: 50~79%  |  ISO 27005: Moderate
     * 권장 조치: 월 1회 점검, 보관 기간 관리
     */
    MEDIUM(14.0, 24.0, "Medium Risk", "보통", "월 1회 점검, 보관 기간 관리"),

    /**
     * High Risk — 위험
     * 점수 구간: 24.0 이상 ~ 36.0 미만
     * NIST 백분위: 80~95%  |  ISO 27005: Major
     * 권장 조치: 1주일 내 조치, 노출 범위 축소 요청
     */
    HIGH(24.0, 36.0, "High Risk", "위험", "1주일 내 조치, 노출 범위 축소 요청"),

    /**
     * Very High Risk — 매우 위험
     * 점수 구간: 36.0 이상 ~ 45.5 이하
     * NIST 백분위: 96~100%  |  ISO 27005: Critical
     * 권장 조치: 즉시 동의 해지, 데이터 삭제 요청
     */
    VERY_HIGH(36.0, 45.5, "Very High Risk", "매우 위험", "즉시 동의 해지, 데이터 삭제 요청");

    /** 해당 등급의 점수 하한값 (이상, inclusive) */
    public final double minScore;

    /** 해당 등급의 점수 상한값 (미만, exclusive) — VERY_HIGH는 최댓값 45.5 포함 */
    public final double maxScore;

    public final String englishLabel;
    public final String koreanLabel;
    public final String recommendedAction;

    RiskGrade(double minScore, double maxScore, String englishLabel,
              String koreanLabel, String recommendedAction) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.englishLabel = englishLabel;
        this.koreanLabel = koreanLabel;
        this.recommendedAction = recommendedAction;
    }

    /**
     * 점수를 입력받아 해당하는 위험 등급을 반환한다.
     *
     * @param score Risk Score (3.0 ~ 45.5)
     * @return 대응하는 RiskGrade
     * @throws IllegalArgumentException 유효 범위(3.0~45.5) 벗어날 경우
     */
    public static RiskGrade fromScore(double score) {
        if (score < 3.0 || score > 45.5) {
            throw new IllegalArgumentException(
                    "유효하지 않은 점수입니다: " + score + " (유효 범위: 3.0 ~ 45.5)");
        }
        for (RiskGrade grade : values()) {
            if (score >= grade.minScore && (score < grade.maxScore || grade == VERY_HIGH)) {
                return grade;
            }
        }
        // 이론적으로 도달 불가
        throw new IllegalArgumentException("등급 분류 실패: " + score);
    }
}
