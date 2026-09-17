package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 기업상세 '위험도' 탭 및 위험기관리스트 상세 카드에 표시되는 위험도 분석 정보.
 */
@Serializable
data class RiskAnalysis(
    /**
     * "어떤 정보를 어떤 목적으로 가져가서 위험한지"를 설명하는 사용자 친화 문구.
     * 점수·변수보다 위에 먼저 보여준다. 서버가 아직 분석하지 않았으면 null이고, 그때는 칸 자체를 숨긴다.
     * 동의 철회로 점수가 바뀌어도 이 문구는 서버 원문 그대로 유지한다 — 재산출 대상이 아니다.
     */
    val summary: String? = null,
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
