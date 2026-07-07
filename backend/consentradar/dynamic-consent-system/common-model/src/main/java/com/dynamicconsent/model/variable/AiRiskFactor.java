package com.dynamicconsent.model.variable;

/**
 * AI (AI Risk Factor, AI 위험계수)
 *
 * 개인정보 처리에 인공지능 기술이 활용될 때 발생하는 추가 위험.
 * 본 모델의 독자적 기여 항목 — 기존 PIA/FAIR/ISO 27005에 없는 신규 변수.
 *
 * 근거:
 * - EU AI Act: 생체인식, 신용평가, 자동화 프로파일링 등 고위험 AI 법적 분류
 * - Carlini et al.(2021): AI 모델이 학습 데이터에서 개인정보 역추출 가능 실증
 *
 * PC와 동일하게 PIA 법적준거성 등급(1.0/1.5)과 대응.
 * 점수 범위: 1.0, 1.5
 */
public enum AiRiskFactor {

    /**
     * Limited/Minimal Risk (1.0점)
     * AI 미활용 또는 저위험 AI 사용.
     * 예: 챗봇, 단순 추천, AI 미활용 처리
     */
    LOW_RISK(1.0, "Limited/Minimal Risk", "AI 미활용 또는 저위험 AI", "챗봇, 단순 추천, AI 미활용 처리"),

    /**
     * High-Risk AI (1.5점)
     * EU AI Act 고위험 AI 분류.
     * 예: 생체인식, 신용평가, 채용 자동화, 자동화 프로파일링, 맞춤형 광고 AI
     */
    HIGH_RISK(1.5, "High-Risk AI", "EU AI Act 고위험 AI — 자동화 프로파일링, 생체인식 등",
            "생체인식, 신용평가, 채용 자동화, 자동화 프로파일링, 맞춤형 광고 AI");

    public final double score;
    public final String euAiActLabel;
    public final String description;
    public final String examples;

    AiRiskFactor(double score, String euAiActLabel, String description, String examples) {
        this.score = score;
        this.euAiActLabel = euAiActLabel;
        this.description = description;
        this.examples = examples;
    }
}
