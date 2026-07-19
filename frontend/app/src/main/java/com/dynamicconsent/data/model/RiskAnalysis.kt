package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 기업상세 '위험도' 탭 및 위험기관리스트 상세 카드에 표시되는 위험도 분석 정보.
 */
@Serializable
data class RiskAnalysis(
    val riskScore: Double,
    val riskGrade: RiskGrade,
    val formula: String,
    val factors: List<RiskFactor>,
    val withdrawalEffects: List<WithdrawalEffect>,
    val maxEffect: MaxEffect,
)

@Serializable
data class RiskFactor(
    val label: String,
    val value: String,
    val description: String,
)

@Serializable
data class WithdrawalEffect(
    val consentTitle: String,
    val pointsReduced: String,
)

@Serializable
data class MaxEffect(
    val currentScore: String,
    val currentGrade: RiskGrade,
    val afterScore: String,
    val afterGrade: RiskGrade,
    val totalReduction: String,
)
