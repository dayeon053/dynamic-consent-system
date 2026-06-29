package com.dynamicconsent.model.variable;

/**
 * PC (Purpose Clarity, 목적 명확성)
 *
 * 개인정보 처리 목적이 법적 요건에 부합하게 명확히 정의되어 있는지.
 * GDPR Article 5 목적 제한 원칙 기반.
 * PIA 법적준거성 등급(준수 1.0 / 위반 1.5)과 1:1 대응.
 * 이진 판단(Compliant vs Non-Compliant)으로 2단계 설계.
 *
 * 점수 범위: 1.0, 1.5
 */
public enum PurposeClarity {

    /**
     * Compliant (1.0점) — 목적 명확
     * 예: 회원 관리, 상품 배송, 법적 의무 이행
     */
    COMPLIANT(1.0, "Compliant", "목적 명확", "회원 관리, 상품 배송, 법적 의무 이행"),

    /**
     * Non-Compliant (1.5점) — 목적 불명확
     * 사후 광범위 악용 가능성 높음.
     * 예: 마케팅 및 기타 목적, 서비스 개선, 포괄적 동의
     */
    NON_COMPLIANT(1.5, "Non-Compliant", "목적 불명확, 포괄적 표현 사용", "마케팅 및 기타 목적, 서비스 개선, 포괄적 동의");

    public final double score;
    public final String complianceLabel;
    public final String description;
    public final String examples;

    PurposeClarity(double score, String complianceLabel, String description, String examples) {
        this.score = score;
        this.complianceLabel = complianceLabel;
        this.description = description;
        this.examples = examples;
    }
}
