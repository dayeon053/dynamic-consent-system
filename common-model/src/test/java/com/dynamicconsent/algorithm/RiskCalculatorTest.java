package com.dynamicconsent.algorithm;

import com.dynamicconsent.model.RiskGrade;
import com.dynamicconsent.model.RiskInput;
import com.dynamicconsent.model.RiskResult;
import com.dynamicconsent.model.variable.AiRiskFactor;
import com.dynamicconsent.model.variable.DataSensitivity;
import com.dynamicconsent.model.variable.ExposureScope;
import com.dynamicconsent.model.variable.PurposeClarity;
import com.dynamicconsent.model.variable.TimeFactor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 위험도 산출 공식(2-1) 회귀 테스트
 *
 * 산정식: Risk Score = DS + (ES × TF × PC × AI) × 2
 * 점수 범위: 3.0 ~ 45.5
 */
class RiskCalculatorTest {

    private static final double DELTA = 1e-9;

    /** 5대 변수 최저값 → 이론적 최솟값 3.0 */
    private static RiskInput minInput() {
        return new RiskInput(
                DataSensitivity.LOW,        // 1
                ExposureScope.LOW,          // 1
                TimeFactor.SHORT,           // 1
                PurposeClarity.COMPLIANT,   // 1.0
                AiRiskFactor.LOW_RISK);     // 1.0
    }

    /** 5대 변수 최고값 → 이론적 최댓값 45.5 */
    private static RiskInput maxInput() {
        return new RiskInput(
                DataSensitivity.HIGH,       // 5
                ExposureScope.HIGH,         // 3
                TimeFactor.LONG,            // 3
                PurposeClarity.NON_COMPLIANT, // 1.5
                AiRiskFactor.HIGH_RISK);    // 1.5
    }

    // ── 산출 공식 경계값 ───────────────────────────────────────────

    @Test
    void calculatesTheoreticalMinimum() {
        // 1 + (1 × 1 × 1.0 × 1.0) × 2 = 3.0
        RiskResult result = RiskCalculator.calculate(minInput());
        assertEquals(3.0, result.getScore(), DELTA);
        assertEquals(RiskGrade.VERY_LOW, result.getGrade());
    }

    @Test
    void calculatesTheoreticalMaximum() {
        // 5 + (3 × 3 × 1.5 × 1.5) × 2 = 5 + 40.5 = 45.5
        RiskResult result = RiskCalculator.calculate(maxInput());
        assertEquals(45.5, result.getScore(), DELTA);
        assertEquals(RiskGrade.VERY_HIGH, result.getGrade());
    }

    @Test
    void calculatesRepresentativeMidCase() {
        // 3 + (2 × 2 × 1.0 × 1.0) × 2 = 3 + 8 = 11.0 → LOW
        RiskInput input = new RiskInput(
                DataSensitivity.MODERATE,   // 3
                ExposureScope.MEDIUM,       // 2
                TimeFactor.MEDIUM,          // 2
                PurposeClarity.COMPLIANT,   // 1.0
                AiRiskFactor.LOW_RISK);     // 1.0
        RiskResult result = RiskCalculator.calculate(input);
        assertEquals(11.0, result.getScore(), DELTA);
        assertEquals(RiskGrade.LOW, result.getGrade());
    }

    @Test
    void appliesCompoundFactorAndDecimalRounding() {
        // 5 + (1 × 3 × 1.5 × 1.5) × 2 = 5 + 13.5 = 18.5 → MEDIUM
        RiskInput input = new RiskInput(
                DataSensitivity.HIGH,       // 5
                ExposureScope.LOW,          // 1
                TimeFactor.LONG,            // 3
                PurposeClarity.NON_COMPLIANT, // 1.5
                AiRiskFactor.HIGH_RISK);    // 1.5
        RiskResult result = RiskCalculator.calculate(input);
        assertEquals(18.5, result.getScore(), DELTA);
        assertEquals(RiskGrade.MEDIUM, result.getGrade());
    }

    // ── calculateMax (동의 항목 다수 → 대표 위험도) ───────────────

    @Test
    void calculateMaxReturnsHighestScoringResult() {
        List<RiskInput> inputs = List.of(minInput(), maxInput(), minInput());
        RiskResult result = RiskCalculator.calculateMax(inputs);
        assertEquals(45.5, result.getScore(), DELTA);
        assertEquals(RiskGrade.VERY_HIGH, result.getGrade());
    }

    @Test
    void calculateMaxRejectsNullList() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculateMax(null));
    }

    @Test
    void calculateMaxRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculateMax(List.of()));
    }

    // ── combineImpacts (2026-07-26 팀 결정 — 정본 합산 방식) ──────

    @Test
    void combineImpactsSynthesizesPerVariableMaximumAcrossItems() {
        // 설계 문서 예시 조합(DS=3,ES=3,TF=3,PC=1.5,AI=1.5)을 두 항목에서 변수별로
        // 나눠 재현한다 — 항목 하나를 통째로 고르는 게 아니라 변수마다 최댓값을 따로
        // 뽑아 합성한다는 걸 증명하기 위해 각 변수의 최댓값 출처를 서로 다른 항목에 둔다.
        RiskInput itemA = new RiskInput(
                DataSensitivity.MODERATE,     // 3 ← 이 항목에서 채택
                ExposureScope.LOW,            // 1
                TimeFactor.LONG,              // 3 ← 이 항목에서 채택
                PurposeClarity.COMPLIANT,     // 1.0
                AiRiskFactor.HIGH_RISK);      // 1.5 ← 이 항목에서 채택
        RiskInput itemB = new RiskInput(
                DataSensitivity.LOW,          // 1
                ExposureScope.HIGH,           // 3 ← 이 항목에서 채택
                TimeFactor.SHORT,             // 1
                PurposeClarity.NON_COMPLIANT, // 1.5 ← 이 항목에서 채택
                AiRiskFactor.LOW_RISK);       // 1.0

        // 합성 결과: DS=3, ES=3, TF=3, PC=1.5, AI=1.5 -> 3 + (3×3×1.5×1.5)×2 = 43.5
        RiskResult result = RiskCalculator.combineImpacts(List.of(itemA, itemB));

        assertEquals(43.5, result.getScore(), DELTA);
        assertEquals(RiskGrade.VERY_HIGH, result.getGrade());
    }

    @Test
    void combineImpactsDiffersFromCalculateMax_whenNoSingleItemDominatesEveryVariable() {
        // calculateMax는 "항목별 점수 중 최댓값"(가장 위험한 항목 하나의 점수)을,
        // combineImpacts는 "변수별 최댓값을 먼저 합성한 뒤 1회 계산"을 쓴다. 어느 항목도
        // 모든 변수에서 동시에 1등이 아니면 두 방식의 결과가 달라져야 한다 — 두 방식이
        // 실제로 다른 알고리즘이라는 걸 증명하는 회귀 테스트.
        RiskInput itemA = new RiskInput(
                DataSensitivity.MODERATE, ExposureScope.HIGH, TimeFactor.SHORT,
                PurposeClarity.COMPLIANT, AiRiskFactor.LOW_RISK); // 3+(3×1×1×1)×2=9.0
        RiskInput itemB = new RiskInput(
                DataSensitivity.LOW, ExposureScope.LOW, TimeFactor.LONG,
                PurposeClarity.NON_COMPLIANT, AiRiskFactor.HIGH_RISK); // 1+(1×3×1.5×1.5)×2=14.5
        List<RiskInput> inputs = List.of(itemA, itemB);

        RiskResult maxResult = RiskCalculator.calculateMax(inputs);
        RiskResult combinedResult = RiskCalculator.combineImpacts(inputs);

        assertEquals(14.5, maxResult.getScore(), DELTA, "calculateMax는 항목B의 점수를 그대로 채택");
        // 변수별 최댓값 합성: DS=3,ES=3,TF=3,PC=1.5,AI=1.5 -> 3+(3×3×1.5×1.5)×2=43.5
        assertEquals(43.5, combinedResult.getScore(), DELTA, "combineImpacts는 변수별 최댓값을 합성");
        assertNotEquals(maxResult.getScore(), combinedResult.getScore(),
                "두 방식은 서로 다른 알고리즘이므로 이 케이스에서 결과가 달라야 한다");
    }

    @Test
    void combineImpactsRejectsNullList() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.combineImpacts(null));
    }

    @Test
    void combineImpactsRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.combineImpacts(List.of()));
    }

    // ── calculateRevocationEffect (동의 철회 효과) ────────────────

    @Test
    void calculateRevocationEffectReturnsScoreDrop() {
        // 철회 전 45.5, 철회 후(ES를 HIGH→LOW) 18.5 → 감소 27.0
        RiskInput original = maxInput();
        RiskInput afterRevocation = new RiskInput(
                DataSensitivity.HIGH,       // 5
                ExposureScope.LOW,          // 1 (철회로 노출 범위 축소)
                TimeFactor.LONG,            // 3
                PurposeClarity.NON_COMPLIANT, // 1.5
                AiRiskFactor.HIGH_RISK);    // 1.5
        double effect = RiskCalculator.calculateRevocationEffect(original, afterRevocation);
        assertEquals(27.0, effect, DELTA);
    }

    @Test
    void calculateRevocationEffectIsZeroWhenUnchanged() {
        RiskInput input = maxInput();
        double effect = RiskCalculator.calculateRevocationEffect(input, input);
        assertEquals(0.0, effect, DELTA);
    }

    // ── null 검증 ─────────────────────────────────────────────────

    @Test
    void calculateRejectsNullInput() {
        assertThrows(NullPointerException.class,
                () -> RiskCalculator.calculate(null));
    }

    @Test
    void calculateRejectsNullVariable() {
        // DS만 null인 부분 입력 → 명시적 NPE
        RiskInput input = new RiskInput(
                null,
                ExposureScope.LOW,
                TimeFactor.SHORT,
                PurposeClarity.COMPLIANT,
                AiRiskFactor.LOW_RISK);
        assertThrows(NullPointerException.class,
                () -> RiskCalculator.calculate(input));
    }
}
