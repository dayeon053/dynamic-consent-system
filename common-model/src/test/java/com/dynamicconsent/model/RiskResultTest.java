package com.dynamicconsent.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RiskResult DB 저장용 변환 헬퍼 테스트 (타입 불일치 방지)
 */
class RiskResultTest {

    @Test
    void scoreAsBigDecimalFixesScaleToTwo() {
        // double 45.5 → BigDecimal "45.50" (컬럼 precision 5, scale 2 대응)
        RiskResult result = new RiskResult(45.5, RiskGrade.VERY_HIGH);
        assertEquals(new BigDecimal("45.50"), result.getScoreAsBigDecimal());
    }

    @Test
    void scoreAsBigDecimalAvoidsFloatingPointNoise() {
        // new BigDecimal(3.0) 과 달리 부동소수 오차 없이 정확히 "3.00"
        RiskResult result = new RiskResult(3.0, RiskGrade.VERY_LOW);
        assertEquals(new BigDecimal("3.00"), result.getScoreAsBigDecimal());
    }

    @Test
    void gradeNameMatchesEnumConstant() {
        RiskResult result = new RiskResult(24.0, RiskGrade.HIGH);
        assertEquals("HIGH", result.getGradeName());
    }
}
