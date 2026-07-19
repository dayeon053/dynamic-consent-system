package com.dynamicconsent.domain

import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.model.RiskVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * common-model RiskScoreAlgorithm_Pseudocode의 경계값 검증 테이블을 그대로 옮긴 테스트.
 * 산정식: Risk Score = DS + (ES × TF × PC × AI) × 2
 */
class RiskCalculatorTest {

    @Test
    fun `슈도코드 경계값 검증 테이블과 산출 결과가 일치한다`() {
        val table = listOf(
            // (변수 조합, 예상 점수, 예상 등급)
            Triple(RiskVariables(ds = 1, es = 1, tf = 1, pc = 1.0, ai = 1.0), 3.0, RiskGrade.VERY_LOW),
            Triple(RiskVariables(ds = 1, es = 2, tf = 2, pc = 1.0, ai = 1.0), 9.0, RiskGrade.LOW),
            Triple(RiskVariables(ds = 3, es = 2, tf = 2, pc = 1.0, ai = 1.0), 11.0, RiskGrade.LOW),
            Triple(RiskVariables(ds = 3, es = 2, tf = 2, pc = 1.5, ai = 1.0), 15.0, RiskGrade.MEDIUM),
            // 슈도코드 원본 테이블은 30.5로 적혀 있으나 수식대로면 3 + (3×3×1.5×1.0)×2 = 30.0 (등급은 동일)
            Triple(RiskVariables(ds = 3, es = 3, tf = 3, pc = 1.5, ai = 1.0), 30.0, RiskGrade.HIGH),
            Triple(RiskVariables(ds = 3, es = 3, tf = 3, pc = 1.5, ai = 1.5), 43.5, RiskGrade.VERY_HIGH),
            Triple(RiskVariables(ds = 5, es = 3, tf = 3, pc = 1.5, ai = 1.5), 45.5, RiskGrade.VERY_HIGH),
        )

        table.forEach { (variables, expectedScore, expectedGrade) ->
            val score = RiskCalculator.calculateScore(variables)
            assertEquals("$variables 점수 불일치", expectedScore, score, 0.0)
            assertEquals("$variables 등급 불일치", expectedGrade, RiskCalculator.classifyGrade(score))
        }
    }

    @Test
    fun `등급 경계값에서 상위 등급으로 분류된다`() {
        assertEquals(RiskGrade.VERY_LOW, RiskCalculator.classifyGrade(6.9))
        assertEquals(RiskGrade.LOW, RiskCalculator.classifyGrade(7.0))
        assertEquals(RiskGrade.LOW, RiskCalculator.classifyGrade(13.9))
        assertEquals(RiskGrade.MEDIUM, RiskCalculator.classifyGrade(14.0))
        assertEquals(RiskGrade.MEDIUM, RiskCalculator.classifyGrade(23.9))
        assertEquals(RiskGrade.HIGH, RiskCalculator.classifyGrade(24.0))
        assertEquals(RiskGrade.HIGH, RiskCalculator.classifyGrade(35.9))
        assertEquals(RiskGrade.VERY_HIGH, RiskCalculator.classifyGrade(36.0))
        assertEquals(RiskGrade.VERY_HIGH, RiskCalculator.classifyGrade(45.5))
    }

    @Test
    fun `유효 범위 밖 점수는 등급 분류에서 예외가 발생한다`() {
        assertThrows(IllegalArgumentException::class.java) { RiskCalculator.classifyGrade(2.9) }
        assertThrows(IllegalArgumentException::class.java) { RiskCalculator.classifyGrade(45.6) }
    }

    @Test
    fun `동의 항목이 없으면 모든 변수가 최솟값으로 합성된다`() {
        val combined = RiskCalculator.combineImpacts(emptyList())

        assertEquals(RiskVariables(), combined)
        assertEquals(3.0, RiskCalculator.calculateScore(combined), 0.0)
    }

    @Test
    fun `변수 합성은 항목별 위험 수준의 최댓값을 따른다`() {
        val combined = RiskCalculator.combineImpacts(
            listOf(
                RiskVariables(ds = 3, es = 1, tf = 1, pc = 1.0, ai = 1.0),
                RiskVariables(ds = 1, es = 3, tf = 2, pc = 1.5, ai = 1.0),
                RiskVariables(ds = 1, es = 2, tf = 3, pc = 1.0, ai = 1.5),
            ),
        )

        assertEquals(RiskVariables(ds = 3, es = 3, tf = 3, pc = 1.5, ai = 1.5), combined)
    }
}
