package com.dynamicconsent.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET /users/{userId}/consents/history 응답 항목 (backend UserConsentHistoryItemDto 대응).
 *
 * 4-7(동의 변경 내역 탭)의 단일 소스(api_spec_v2_final.md 확정 사항 4번) — PATCH(2-3)
 * 응답에는 changed_at이 없어 이 API로만 이력을 가져온다. 전체 기업 통합 응답이며 변경
 * 시각 오름차순으로 내려온다.
 */
@Serializable
data class ConsentHistoryResponse(
    val consentItemId: Long,
    val itemName: String,
    val companyId: Long,
    val companyName: String,
    val isChecked: Boolean,
    /** 타임존 표기 없는 KST. NoticeResponse.crawledAt과 동일한 해석 기준. */
    val changedAt: String,
)
