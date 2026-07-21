package com.dynamicconsent.data.model

/**
 * 사용자가 선택동의 스위치를 조작한 기록.
 * 기업상세 '동의 변경 내역' 탭에 날짜별로 그룹핑되어 표시된다. (세션 내 인메모리 기록)
 */
data class ConsentChangeRecord(
    val consentTitle: String,
    val enabled: Boolean,
    val timestampMillis: Long,
)
