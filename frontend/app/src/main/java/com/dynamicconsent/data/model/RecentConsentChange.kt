package com.dynamicconsent.data.model

/**
 * 홈 화면 '최근 동의 변경 내역'에 보여줄 기록 1건.
 *
 * 기업상세의 [ConsentChangeRecord]와 달리 **여러 기업을 한 줄에 섞어 보여주므로**
 * 어느 기업의 변경인지 함께 들고 있어야 한다.
 */
data class RecentConsentChange(
    val companyName: String,
    val consentTitle: String,
    val enabled: Boolean,
    val timestampMillis: Long,
)
