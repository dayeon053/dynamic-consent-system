package com.dynamicconsent.data.model

/**
 * 기업상세 '동의 세부 사항' 탭에 표시되는 선택/필수 동의 목록.
 */
data class ConsentDetail(
    val optionalConsents: List<ConsentToggleItem>,
    val requiredConsents: List<ConsentRequiredItem>,
)

data class ConsentToggleItem(
    val id: Int,
    val title: String,
    val enabled: Boolean,
)

data class ConsentRequiredItem(
    val id: Int,
    val title: String,
)
