package com.dynamicconsent.model.variable;

/**
 * TF (Time Factor, 경과 시간)
 *
 * 개인정보 보관 기간이 길수록 침해 위험이 증가한다는 실증 데이터 반영.
 * Silva et al.(2022) 2,000건 실제 유출 사건 분석 기반 (6개월 / 12개월 임계점).
 * NIA(2021): 6개월 이상 1.5배, 12개월 초과 2.0배 리스크 증가 실증.
 * 개인정보보호법 제21조(목적 달성 후 즉시 파기 원칙) 반영.
 *
 * 점수 범위: 1, 2, 3
 */
public enum TimeFactor {

    /**
     * 단기 보관 (1점) — 6개월 미만
     * Vatsalan et al.(2022): 0~3개월 구간 낮은 위험
     */
    SHORT(1, "6개월 미만", "단기 보관, 낮은 위험"),

    /**
     * 중기 보관 (2점) — 6~12개월
     * NIA(2021): 6개월 이상 리스크 1.5배 증가
     */
    MEDIUM(2, "6~12개월", "중기 보관, 리스크 1.5배 증가"),

    /**
     * 장기 보관 (3점) — 12개월 초과
     * Silva et al.(2022): 12개월 이상 보관 시 최대 리스크
     * 개인정보보호법 제21조 위반 가능성 내포
     */
    LONG(3, "12개월 초과", "장기 보관, 최대 리스크 / 법적 위반 가능성");

    public final int score;
    public final String period;
    public final String description;

    TimeFactor(int score, String period, String description) {
        this.score = score;
        this.period = period;
        this.description = description;
    }
}
