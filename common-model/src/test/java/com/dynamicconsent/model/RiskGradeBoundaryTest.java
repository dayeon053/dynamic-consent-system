package com.dynamicconsent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskGradeBoundaryTest {

    @Test
    void boundaryValuesAssignCorrectGrades() {
        // VERY_LOW: 3.0 이상 ~ 7.0 미만
        assertEquals(RiskGrade.VERY_LOW, RiskGrade.fromScore(3.0));
        assertEquals(RiskGrade.VERY_LOW, RiskGrade.fromScore(6.9));

        // LOW: 7.0 이상 ~ 14.0 미만
        assertEquals(RiskGrade.LOW, RiskGrade.fromScore(7.0));
        assertEquals(RiskGrade.LOW, RiskGrade.fromScore(13.9));

        // MEDIUM: 14.0 이상 ~ 24.0 미만
        assertEquals(RiskGrade.MEDIUM, RiskGrade.fromScore(14.0));
        assertEquals(RiskGrade.MEDIUM, RiskGrade.fromScore(23.9));

        // HIGH: 24.0 이상 ~ 36.0 미만
        assertEquals(RiskGrade.HIGH, RiskGrade.fromScore(24.0));
        assertEquals(RiskGrade.HIGH, RiskGrade.fromScore(35.9));

        // VERY_HIGH: 36.0 이상 ~ 45.5 이하
        assertEquals(RiskGrade.VERY_HIGH, RiskGrade.fromScore(36.0));
        assertEquals(RiskGrade.VERY_HIGH, RiskGrade.fromScore(45.5));
    }
}
