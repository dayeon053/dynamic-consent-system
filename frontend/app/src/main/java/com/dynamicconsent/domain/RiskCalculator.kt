package com.dynamicconsent.domain

import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.model.RiskVariables
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 위험도 산출 수식 및 5단계 등급 분류의 클라이언트 사이드 이식.
 * common-model의 RiskScoreAlgorithm_Pseudocode(팀원3 확정 스펙)를 그대로 옮긴 것으로,
 * 산정식과 등급 경계값이 변경되면 반드시 common-model과 함께 수정해야 한다.
 *
 * 산정식: Risk Score = DS + (ES × TF × PC × AI) × 2
 * 점수 범위: 3.0 ~ 45.5
 */
object RiskCalculator {

    const val MIN_SCORE = 3.0
    const val MAX_SCORE = 45.5

    /** 5대 변수로부터 위험도 점수를 산출한다. 소수점 첫째 자리 반올림. */
    fun calculateScore(variables: RiskVariables): Double {
        val compoundFactor = variables.es * variables.tf * variables.pc * variables.ai
        val rawScore = variables.ds + compoundFactor * 2
        val score = (rawScore * 10).roundToInt() / 10.0
        check(score in MIN_SCORE..MAX_SCORE) { "산출 점수 범위 초과: $score" }
        return score
    }

    /** 점수를 5단계 등급으로 분류한다. 경계값은 common-model RiskGrade와 동일. */
    fun classifyGrade(score: Double): RiskGrade {
        require(score in MIN_SCORE..MAX_SCORE) {
            "유효하지 않은 점수입니다: $score (유효 범위: $MIN_SCORE ~ $MAX_SCORE)"
        }
        return when {
            score >= 36.0 -> RiskGrade.VERY_HIGH
            score >= 24.0 -> RiskGrade.HIGH
            score >= 14.0 -> RiskGrade.MEDIUM
            score >= 7.0 -> RiskGrade.LOW
            else -> RiskGrade.VERY_LOW
        }
    }

    /**
     * 동의 중인 항목들의 변수 기여도를 하나의 변수 묶음으로 합성한다.
     * 각 변수는 동의된 항목들이 유발하는 위험 수준 중 최댓값을 따르고,
     * 아무 항목에도 동의하지 않으면 모든 변수가 최솟값(3.0점)이 된다.
     */
    fun combineImpacts(impacts: List<RiskVariables>): RiskVariables =
        impacts.fold(RiskVariables()) { acc, impact ->
            RiskVariables(
                ds = max(acc.ds, impact.ds),
                es = max(acc.es, impact.es),
                tf = max(acc.tf, impact.tf),
                pc = max(acc.pc, impact.pc),
                ai = max(acc.ai, impact.ai),
            )
        }
}
