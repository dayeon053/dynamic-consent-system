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
)
