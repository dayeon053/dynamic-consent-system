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
)

@Serializable
data class ConsentRequiredItem(
    val id: Int,
    val title: String,
)
