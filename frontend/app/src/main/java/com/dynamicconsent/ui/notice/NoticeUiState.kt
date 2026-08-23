package com.dynamicconsent.ui.notice

import com.dynamicconsent.data.model.Notice

data class NoticeUiState(
    val isLoading: Boolean = false,
    val notices: List<Notice> = emptyList(),
    /** 목록 조회 실패 메시지. null이면 정상. */
    val error: String? = null,
)
