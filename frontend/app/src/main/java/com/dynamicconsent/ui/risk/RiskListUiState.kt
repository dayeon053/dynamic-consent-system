package com.dynamicconsent.ui.risk

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail

data class RiskListUiState(
    val isLoading: Boolean = false,
    val organizations: List<Organization> = emptyList(),
    val selectedOrganizationId: String? = null,
    val selectedDetail: OrganizationDetail? = null,
    /** 데이터 로드 실패 메시지. null이면 정상. */
    val error: String? = null,
    /**
     * 서버 조회에 실패해 mock 데이터로 채워졌는지.
     * true면 화면에 "오프라인 데이터"임을 밝힌다 — 사용자의 동의 철회가 반영되지 않은 초기값이라
     * 아무 표시 없이 보여주면 틀린 점수를 사실처럼 보여주게 된다.
     */
    val isFallback: Boolean = false,
)
