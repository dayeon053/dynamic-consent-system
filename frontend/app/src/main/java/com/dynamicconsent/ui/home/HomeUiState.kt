package com.dynamicconsent.ui.home

import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.data.model.RecentConsentChange

data class HomeUiState(
    val isLoading: Boolean = false,
    /** 위험도가 감지된 기관 수 (홈 상단 카드) */
    val riskyOrganizationCount: Int = 0,
    /** 실제 데이터에 존재하는 카테고리 목록 — 바로가기 버튼으로 쓴다 */
    val categories: List<String> = emptyList(),
    val recentChanges: List<RecentConsentChange> = emptyList(),
    val notices: List<Notice> = emptyList(),
    /** 목록 로드 실패 메시지. null이면 정상. */
    val error: String? = null,
)
