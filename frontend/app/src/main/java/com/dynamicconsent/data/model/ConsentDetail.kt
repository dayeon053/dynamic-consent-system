package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 기업상세 '동의 세부 사항' 탭에 표시되는 선택/필수 동의 목록.
 */
@Serializable
data class ConsentDetail(
    val optionalConsents: List<ConsentToggleItem>,
    val requiredConsents: List<ConsentRequiredItem>,
)

@Serializable
data class ConsentToggleItem(
    val id: Int,
    val title: String,
    val enabled: Boolean,
    /** 이 항목에 동의했을 때 유발되는 5대 변수 위험 수준. 철회 시 위험도 재계산에 사용. */
    val variableImpact: RiskVariables = RiskVariables(),
)

@Serializable
data class ConsentRequiredItem(
    val id: Int,
    val title: String,
    /**
     * 필수동의 항목이 유발하는 5대 변수 위험 수준.
     * 철회할 수 없는 항목이므로 위험도 계산에서 **항상** 반영된다.
     * (기본값은 최솟값이라 값이 채워지기 전까지는 기존 점수와 동일하다.)
     */
    val variableImpact: RiskVariables = RiskVariables(),
)
