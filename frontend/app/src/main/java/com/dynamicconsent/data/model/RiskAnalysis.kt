package com.dynamicconsent.data.model

/**
 * 기업상세 '위험도' 탭 및 위험기관리스트 상세 카드에 표시되는 위험도 분석 정보.
 */
data class RiskAnalysis(
    val riskScore: Double,
    val riskGrade: RiskGrade,
    val formula: String,
    val factors: List<RiskFactor>,
    val withdrawalEffects: List<WithdrawalEffect>,
    val maxEffect: MaxEffect,
)

data class RiskFactor(
    val label: String,
    val value: String,
    val description: String,
)

data class WithdrawalEffect(
    val consentTitle: String,
    val pointsReduced: String,
)

data class MaxEffect(
    val currentScore: String,
    val currentGrade: RiskGrade,
    val afterScore: String,
    val afterGrade: RiskGrade,
    val totalReduction: String,
)
