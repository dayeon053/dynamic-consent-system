package com.dynamicconsent.data.model

import kotlinx.serialization.Serializable

/**
 * 위험도 산출 5대 변수 값 묶음.
 * common-model의 RiskInput에 대응하며, 유효 값 범위는 위험도 알고리즘 스펙을 따른다.
 * 기본값은 모두 최솟값(= 아무 동의도 없는 상태, 3.0점)이다.
 */
@Serializable
data class RiskVariables(
    val ds: Int = 1,     // 데이터민감도 {1, 3, 5}
    val es: Int = 1,     // 노출범위 {1, 2, 3}
    val tf: Int = 1,     // 경과시간 {1, 2, 3}
    val pc: Double = 1.0, // 목적명확성 {1.0, 1.5}
    val ai: Double = 1.0, // AI위험 {1.0, 1.5}
)
