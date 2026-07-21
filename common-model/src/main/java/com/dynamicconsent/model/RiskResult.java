package com.dynamicconsent.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 위험도 산출 결과 모델 — 백엔드 응답 및 앱 UI 표시용
 */
public class RiskResult {

    /** 산출된 위험도 점수 (3.0 ~ 45.5) */
    private final double score;

    /** 5단계 등급 */
    private final RiskGrade grade;

    public RiskResult(double score, RiskGrade grade) {
        this.score = score;
        this.grade = grade;
    }

    public double    getScore() { return score; }
    public RiskGrade getGrade() { return grade; }

    /**
     * DB 저장용 — score(double)를 RiskScore.totalScore 컬럼(precision 5, scale 2)에
     * 맞춰 BigDecimal로 안전 변환한다.
     *
     * {@code new BigDecimal(double)}는 부동소수점 오차(예: 45.5 → 45.4999…)를 그대로
     * 담으므로 금지. {@link BigDecimal#valueOf(double)} + setScale(2) 로 변환한다.
     *
     * @return 소수점 2자리로 고정된 점수 (예: 45.5 → 45.50)
     */
    public BigDecimal getScoreAsBigDecimal() {
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * DB 저장용 — 등급 enum의 name() 문자열.
     * 백엔드 RiskScore.Grade(중복 정의된 enum)와 상수명이 1:1 일치하므로
     * {@code RiskScore.Grade.valueOf(result.getGradeName())} 로 안전하게 매핑된다.
     *
     * @return 등급 상수명 (VERY_LOW / LOW / MEDIUM / HIGH / VERY_HIGH)
     */
    public String getGradeName() {
        return grade.name();
    }

    @Override
    public String toString() {
        return String.format("RiskResult{score=%.1f, grade=%s(%s)}",
                score, grade.englishLabel, grade.koreanLabel);
    }
}
