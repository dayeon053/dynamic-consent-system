package com.dynamicconsent.domain

import com.dynamicconsent.data.model.MaxEffect
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.RiskFactor
import com.dynamicconsent.data.model.RiskVariables
import com.dynamicconsent.data.model.WithdrawalEffect

/**
 * 사용자의 현재 동의 상태를 반영해 기관 상세 데이터 전체(점수·등급·분석 정보)를 재산출한다.
 * 스위치 토글 → 위험도 실시간 재계산의 핵심 로직.
 */
object RiskRecalculator {

    /**
     * [enabledConsentIds]에 담긴 항목만 동의 중이라고 보고 [detail]을 재산출한 사본을 반환한다.
     * organization의 점수/등급, consentDetail의 enabled 상태, riskAnalysis 전체가 갱신된다.
     */
    fun recalculate(detail: OrganizationDetail, enabledConsentIds: Set<Int>): OrganizationDetail {
        val consents = detail.consentDetail.optionalConsents.map {
            it.copy(enabled = it.id in enabledConsentIds)
        }
        // 필수동의는 사용자가 끌 수 없으므로 항상 위험도에 반영한다.
        val requiredImpacts = detail.consentDetail.requiredConsents.map { it.variableImpact }
        val enabledImpacts = requiredImpacts + consents.filter { it.enabled }.map { it.variableImpact }
        val variables = RiskCalculator.combineImpacts(enabledImpacts)
        val score = RiskCalculator.calculateScore(variables)
        val grade = RiskCalculator.classifyGrade(score)

        val withdrawalEffects = consents.filter { it.enabled }.map { item ->
            val withoutItem = requiredImpacts + consents
                .filter { it.enabled && it.id != item.id }
                .map { it.variableImpact }
            val reducedScore = RiskCalculator.calculateScore(RiskCalculator.combineImpacts(withoutItem))
            val delta = round1(score - reducedScore)
            WithdrawalEffect(
                consentTitle = item.title,
                pointsReduced = if (delta > 0.0) "${formatPoints(delta)}점 감소" else "감소 없음",
            )
        }

        // 선택동의를 모두 해지해도 필수동의는 남으므로, 도달 가능한 최저 점수는 필수동의 기준이다.
        val minScore = RiskCalculator.calculateScore(RiskCalculator.combineImpacts(requiredImpacts))
        val maxEffect = MaxEffect(
            currentScore = "${score}점",
            currentGrade = grade,
            afterScore = "${minScore}점",
            afterGrade = RiskCalculator.classifyGrade(minScore),
            totalReduction = "최대 ${formatPoints(round1(score - minScore))}점 감소",
        )

        return detail.copy(
            organization = detail.organization.copy(riskScore = score, riskGrade = grade),
            consentDetail = detail.consentDetail.copy(optionalConsents = consents),
            riskAnalysis = detail.riskAnalysis.copy(
                riskScore = score,
                riskGrade = grade,
                formula = buildFormula(variables, score),
                factors = detail.riskAnalysis.factors.map { it.withValueFrom(variables) },
                withdrawalEffects = withdrawalEffects,
                maxEffect = maxEffect,
            ),
        )
    }

    private fun buildFormula(v: RiskVariables, score: Double): String =
        "위험도 = 데이터민감도(${v.ds}) + (노출범위(${v.es}) × 경과시간(${v.tf}) × " +
            "목적명확성(${formatPoints(v.pc)}) × AI위험(${formatPoints(v.ai)})) × 2 = $score"

    /** 변수 분석 항목의 label을 보고 현재 변수 값으로 value만 갱신한다. 설명은 JSON 원본 유지. */
    private fun RiskFactor.withValueFrom(v: RiskVariables): RiskFactor = when (label) {
        "데이터민감도" -> copy(value = v.ds.toString())
        "노출범위" -> copy(value = v.es.toString())
        "경과시간" -> copy(value = v.tf.toString())
        "목적명확성" -> copy(value = formatPoints(v.pc))
        "AI위험" -> copy(value = formatPoints(v.ai))
        else -> this
    }

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0

    /** 12.0 → "12", 13.5 → "13.5" 처럼 불필요한 소수점을 정리한다. */
    private fun formatPoints(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
