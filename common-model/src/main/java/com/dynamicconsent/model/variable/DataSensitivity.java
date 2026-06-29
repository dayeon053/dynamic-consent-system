package com.dynamicconsent.model.variable;

/**
 * DS (Data Sensitivity, 데이터 민감도)
 *
 * 개인정보 항목이 침해될 경우 정보주체에게 미치는 피해의 심각성.
 * NIST SP 800-122 PII 3단계 분류 및 PIA 자산가치 등급 체계 기반.
 * GDPR Article 9 특별 범주 데이터(인종, 건강, 생체, 유전)는 HIGH(5점) 처리.
 *
 * 점수 범위: 1, 3, 5 (비선형 — 피해 심각성의 지수적 증가 반영)
 */
public enum DataSensitivity {

    /**
     * Low PII (1점)
     * 직접적 피해 제한적.
     * 예: 단독 이름, 단독 이메일, 단독 전화번호
     */
    LOW(1, "Low PII", "직접적 피해 제한적", "단독 이름, 단독 이메일, 단독 전화번호"),

    /**
     * Moderate PII (3점)
     * 프라이버시 침해, 신원 노출 가능한 복합 식별 정보.
     * 예: 이름+생년월일, 주소+전화번호, 고용정보, 행태정보
     */
    MODERATE(3, "Moderate PII", "프라이버시 침해, 신원 노출 가능", "이름+생년월일, 주소+전화번호, 고용정보"),

    /**
     * High PII (5점)
     * 재정적 손실, 신원 도용, 차별, 신체적 위협 초래 가능.
     * GDPR Article 9 특별 범주 데이터 포함.
     * 예: 주민등록번호, 금융계좌, 의료기록, 생체정보, 인종, 유전 데이터
     */
    HIGH(5, "High PII / GDPR Special Category", "재정적 손실, 신원 도용, 차별, 신체적 위협 초래 가능",
            "주민등록번호, 금융계좌, 의료기록, 생체정보");

    public final int score;
    public final String nistLabel;
    public final String description;
    public final String examples;

    DataSensitivity(int score, String nistLabel, String description, String examples) {
        this.score = score;
        this.nistLabel = nistLabel;
        this.description = description;
        this.examples = examples;
    }
}
