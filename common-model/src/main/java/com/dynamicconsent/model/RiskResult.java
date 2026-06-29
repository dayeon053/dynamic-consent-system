package com.dynamicconsent.model;

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

    @Override
    public String toString() {
        return String.format("RiskResult{score=%.1f, grade=%s(%s)}",
                score, grade.englishLabel, grade.koreanLabel);
    }
}
