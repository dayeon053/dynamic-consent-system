package com.dynamicconsent.model.variable;

/**
 * ES (Exposure Scope, 노출 범위)
 *
 * 개인정보가 접근 가능한 대상의 범위.
 * ENISA 데이터 보호 엔지니어링 가이드 Low/Medium/High Risk 3단계 기반.
 * 개인정보보호법 제17조(제3자 제공 시 별도 동의 요구) 반영.
 *
 * 점수 범위: 1, 2, 3
 */
public enum ExposureScope {

    /**
     * Low Risk (1점)
     * 내부 처리만 또는 극히 제한된 접근.
     * 예: 담당자만 접근, 암호화된 내부 저장
     */
    LOW(1, "Low Risk", "내부 처리만 또는 극히 제한된 접근", "담당자만 접근, 암호화된 내부 저장"),

    /**
     * Medium Risk (2점)
     * 제한적 외부 공유 또는 제3자 위탁.
     * 예: 마케팅 대행사, 결제대행사, 협력사 공유
     */
    MEDIUM(2, "Medium Risk", "제한적 외부 공유 또는 제3자 위탁", "마케팅 대행사, 결제대행사, 협력사 공유"),

    /**
     * High Risk (3점)
     * 외부에 광범위하게 노출, 통제 불가.
     * 예: 불특정 다수 공개, 해외 서버 이전, 다수 광고주 제공
     */
    HIGH(3, "High Risk", "외부에 광범위하게 노출, 통제 불가", "불특정 다수 공개, 해외 서버 이전, 다수 광고주 제공");

    public final int score;
    public final String enisaLabel;
    public final String description;
    public final String examples;

    ExposureScope(int score, String enisaLabel, String description, String examples) {
        this.score = score;
        this.enisaLabel = enisaLabel;
        this.description = description;
        this.examples = examples;
    }
}
