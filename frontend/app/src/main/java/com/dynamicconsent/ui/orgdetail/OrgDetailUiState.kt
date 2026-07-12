package com.dynamicconsent.ui.orgdetail

import com.dynamicconsent.data.model.OrganizationDetail

enum class OrgDetailTab(val label: String) {
    CONSENT("동의 세부 사항"),
    RISK("위험도"),
    THIRD_PARTY("제3자 제공"),
    CHANGE_HISTORY("동의 변경 내역"),
    INFO("정보"),
}

data class OrgDetailUiState(
    val isLoading: Boolean = false,
    val detail: OrganizationDetail? = null,
    val activeTab: OrgDetailTab = OrgDetailTab.CONSENT,
)
